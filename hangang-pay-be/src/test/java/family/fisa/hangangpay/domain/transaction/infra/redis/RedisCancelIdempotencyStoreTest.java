package family.fisa.hangangpay.domain.transaction.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.infra.redis.cancel.CancelIdempotencyRecord;
import family.fisa.hangangpay.domain.transaction.infra.redis.cancel.RedisCancelIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyDecisionType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisCancelIdempotencyStoreTest {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final String ORIGINAL_PAYMENT_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_HASH = "20";  // String.valueOf(merchantPartyId)
    private static final String DIFFERENT_REQUEST_HASH = "99";
    private static final String KEY = "cancel:idempotency:" + ORIGINAL_PAYMENT_UUID;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisCancelIdempotencyStore redisCancelIdempotencyStore;

    @BeforeEach
    void setUp() {
        objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        redisCancelIdempotencyStore =
                new RedisCancelIdempotencyStore(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis에 기존 record가 없으면 PROCESSING record를 생성하고 NEW_REQUEST를 반환한다")
    void beginCancel_noExistingRecordReturnsNewRequest() throws Exception {
        // 1. setIfAbsent가 true면 이 요청이 originalPaymentUuid의 첫 취소 요청이다
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(true);

        // 2. 첫 요청이므로 NEW_REQUEST 결정이 반환된다
        CancelIdempotencyDecision decision =
                redisCancelIdempotencyStore.beginCancel(ORIGINAL_PAYMENT_UUID, REQUEST_HASH);

        assertThat(decision.type()).isEqualTo(CancelIdempotencyDecisionType.NEW_REQUEST);
        assertThat(decision.responseSnapshot()).isNull();

        // 3. 첫 요청 선점 시 Redis에는 snapshot 없이 PROCESSING 상태만 저장한다
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        CancelIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.originalPaymentUuid()).isEqualTo(ORIGINAL_PAYMENT_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(saved.responseSnapshot()).isNull();

        verify(valueOperations, never()).get(KEY);
    }

    @Test
    @DisplayName("기존 record와 requestHash가 다르면 CONFLICT를 반환한다")
    void beginCancel_differentHashReturnsConflict() throws Exception {
        // 1. 이미 다른 가맹점(또는 다른 파라미터)의 취소 record가 선점 중이다
        CancelIdempotencyRecord existing =
                record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        // 2. requestHash가 다르면 같은 결제 취소 재시도가 아니다
        CancelIdempotencyDecision decision =
                redisCancelIdempotencyStore.beginCancel(
                        ORIGINAL_PAYMENT_UUID, DIFFERENT_REQUEST_HASH);

        assertThat(decision.type()).isEqualTo(CancelIdempotencyDecisionType.CONFLICT);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("같은 requestHash이고 snapshot이 있으면 RETURN_SNAPSHOT을 반환한다")
    void beginCancel_sameHashWithSnapshotReturnsSnapshot() throws Exception {
        // 1. 이전에 동일 요청이 성공해 snapshot이 저장된 상태다
        PaymentCancelResponse snapshot = successSnapshot();
        CancelIdempotencyRecord existing =
                record(TransactionStatus.SUCCESS, REQUEST_HASH, snapshot);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        // 2. 완료된 동일 요청은 Bank를 다시 호출하지 않고 저장된 응답을 돌려준다
        CancelIdempotencyDecision decision =
                redisCancelIdempotencyStore.beginCancel(ORIGINAL_PAYMENT_UUID, REQUEST_HASH);

        assertThat(decision.type()).isEqualTo(CancelIdempotencyDecisionType.RETURN_SNAPSHOT);
        assertThat(decision.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("같은 requestHash이지만 snapshot이 없으면 PROCESSING을 반환한다")
    void beginCancel_sameHashWithoutSnapshotReturnsProcessing() throws Exception {
        // 1. 동일 요청이 아직 Bank 호출 또는 후처리 중이다
        CancelIdempotencyRecord existing =
                record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        // 2. snapshot 없이 PROCESSING이면 이미 처리 중인 요청 — 재진입 거부
        CancelIdempotencyDecision decision =
                redisCancelIdempotencyStore.beginCancel(ORIGINAL_PAYMENT_UUID, REQUEST_HASH);

        assertThat(decision.type()).isEqualTo(CancelIdempotencyDecisionType.PROCESSING);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("completeCancel은 기존 record에 성공 snapshot을 저장한다")
    void completeCancel_storesSnapshot() throws Exception {
        // 1. 취소 처리 완료 전 PROCESSING 상태 record가 존재한다
        CancelIdempotencyRecord existing =
                record(TransactionStatus.PROCESSING, REQUEST_HASH, null);
        PaymentCancelResponse snapshot = successSnapshot();

        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        // 2. Bank 취소 성공 후 completeCancel 호출
        redisCancelIdempotencyStore.completeCancel(ORIGINAL_PAYMENT_UUID, snapshot);

        // 3. 완료 후에는 동일 요청 재시도를 위해 최종 응답 snapshot을 함께 저장한다
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        CancelIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.originalPaymentUuid()).isEqualTo(ORIGINAL_PAYMENT_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("markCancelStatus는 snapshot 없이 상태만 갱신한다")
    void markCancelStatus_updatesStatusWithoutSnapshot() throws Exception {
        // 1. Bank 타임아웃 등으로 snapshot이 없는 PROCESSING record가 있다
        CancelIdempotencyRecord existing =
                record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        // 2. UNKNOWN으로 상태만 갱신
        // - UNKNOWN 기록이 없으면 다음 재시도가 NEW_REQUEST로 처리되어 Bank를 한 번 더 호출한다
        // - UNKNOWN을 기록해 두면 재시도 시 PROCESSING으로 인식해 스케줄러 복구로 유도한다
        redisCancelIdempotencyStore.markCancelStatus(
                ORIGINAL_PAYMENT_UUID, TransactionStatus.UNKNOWN);

        // 3. snapshot은 null 유지, 상태만 UNKNOWN으로 바뀐다
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        CancelIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.originalPaymentUuid()).isEqualTo(ORIGINAL_PAYMENT_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(saved.responseSnapshot()).isNull();
    }

    // ===== 픽스처 =====

    private CancelIdempotencyRecord record(
            TransactionStatus status,
            String requestHash,
            PaymentCancelResponse responseSnapshot) {
        return new CancelIdempotencyRecord(
                ORIGINAL_PAYMENT_UUID, requestHash, status, responseSnapshot);
    }

    private PaymentCancelResponse successSnapshot() {
        return new PaymentCancelResponse(
                ORIGINAL_PAYMENT_UUID,
                TransactionStatus.SUCCESS,
                "APV-2026-00000100",
                "0x-cancel-snapshot",
                new BigDecimal("10000"),
                LocalDateTime.of(2026, 5, 27, 14, 0));
    }

    private String writeRecord(CancelIdempotencyRecord record) throws Exception {
        return objectMapper.writeValueAsString(record);
    }

    private CancelIdempotencyRecord readRecord(String value) throws Exception {
        return objectMapper.readValue(value, CancelIdempotencyRecord.class);
    }
}
