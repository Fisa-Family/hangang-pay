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
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.infra.redis.charge.ChargeIdempotencyRecord;
import family.fisa.hangangpay.domain.transaction.infra.redis.charge.RedisChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecisionType;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
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
class RedisChargeIdempotencyStoreTest {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final Long TRANSACTION_ID = 123L;
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_HASH = "server-generated-request-hash";
    private static final String DIFFERENT_REQUEST_HASH = "different-request-hash";
    private static final String KEY = "charge:idempotency:" + TRANSACTION_UUID;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisChargeIdempotencyStore redisChargeIdempotencyStore;

    @BeforeEach
    void setUp() {
        objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        redisChargeIdempotencyStore = new RedisChargeIdempotencyStore(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis에 기존 record가 없으면 PROCESSING record를 생성하고 NEW_REQUEST를 반환한다")
    void beginExecution_noExistingRecordReturnsNewRequest() throws Exception {
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(true);

        IdempotencyDecision<ChargeExecuteResponse> decision =
                redisChargeIdempotencyStore.beginExecution(
                        new IdempotencyKey(TRANSACTION_UUID, REQUEST_HASH), TRANSACTION_ID);

        assertThat(decision.type()).isEqualTo(IdempotencyDecisionType.NEW_REQUEST);
        assertThat(decision.responseSnapshot()).isNull();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        ChargeIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(saved.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.responseSnapshot()).isNull();

        verify(valueOperations, never()).get(KEY);
    }

    @Test
    @DisplayName("기존 record와 requestHash가 다르면 CONFLICT를 반환한다")
    void beginExecution_differentHashReturnsConflict() throws Exception {
        ChargeIdempotencyRecord existing = record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        IdempotencyDecision<ChargeExecuteResponse> decision =
                redisChargeIdempotencyStore.beginExecution(
                        new IdempotencyKey(TRANSACTION_UUID, DIFFERENT_REQUEST_HASH),
                        TRANSACTION_ID);

        assertThat(decision.type()).isEqualTo(IdempotencyDecisionType.CONFLICT);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("같은 requestHash이고 snapshot이 있으면 RETURN_SNAPSHOT을 반환한다")
    void beginExecution_sameHashWithSnapshotReturnsSnapshot() throws Exception {
        ChargeExecuteResponse snapshot = successSnapshot();
        ChargeIdempotencyRecord existing =
                record(TransactionStatus.SUCCESS, REQUEST_HASH, snapshot);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        IdempotencyDecision<ChargeExecuteResponse> decision =
                redisChargeIdempotencyStore.beginExecution(
                        new IdempotencyKey(TRANSACTION_UUID, REQUEST_HASH), TRANSACTION_ID);

        assertThat(decision.type()).isEqualTo(IdempotencyDecisionType.RETURN_SNAPSHOT);
        assertThat(decision.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("같은 requestHash이지만 snapshot이 없으면 PROCESSING을 반환한다")
    void beginExecution_sameHashWithoutSnapshotReturnsProcessing() throws Exception {
        ChargeIdempotencyRecord existing = record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        IdempotencyDecision<ChargeExecuteResponse> decision =
                redisChargeIdempotencyStore.beginExecution(
                        new IdempotencyKey(TRANSACTION_UUID, REQUEST_HASH), TRANSACTION_ID);

        assertThat(decision.type()).isEqualTo(IdempotencyDecisionType.PROCESSING);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("충전은 FAILED 상태 record를 재요청해도 ALREADY_FAILED가 아닌 PROCESSING을 반환한다")
    void beginExecution_failedRecordReturnsProcessingNotAlreadyFailed() throws Exception {
        // 충전 스토어는 honorFailedState=false — 기존 동작상 FAILED여도 진행 중으로 본다.
        ChargeIdempotencyRecord existing = record(TransactionStatus.FAILED, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        IdempotencyDecision<ChargeExecuteResponse> decision =
                redisChargeIdempotencyStore.beginExecution(
                        new IdempotencyKey(TRANSACTION_UUID, REQUEST_HASH), TRANSACTION_ID);

        assertThat(decision.type()).isEqualTo(IdempotencyDecisionType.PROCESSING);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("completeExecution은 기존 record에 성공 snapshot을 저장한다")
    void completeExecution_storesSnapshot() throws Exception {
        ChargeIdempotencyRecord existing = record(TransactionStatus.PROCESSING, REQUEST_HASH, null);
        ChargeExecuteResponse snapshot = successSnapshot();

        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        redisChargeIdempotencyStore.completeExecution(TRANSACTION_UUID, snapshot);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        ChargeIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("markExecutionStatus는 snapshot을 유지한 채 상태만 교체한다")
    void markExecutionStatus_updatesStatusOnly() throws Exception {
        ChargeIdempotencyRecord existing = record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        redisChargeIdempotencyStore.markExecutionStatus(TRANSACTION_UUID, TransactionStatus.FAILED);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        ChargeIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.responseSnapshot()).isNull();
    }

    private ChargeIdempotencyRecord record(
            TransactionStatus status, String requestHash, ChargeExecuteResponse responseSnapshot) {
        return new ChargeIdempotencyRecord(
                TRANSACTION_UUID, requestHash, status, TRANSACTION_ID, responseSnapshot);
    }

    private ChargeExecuteResponse successSnapshot() {
        return new ChargeExecuteResponse(
                1L,
                TRANSACTION_ID,
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                new BigDecimal("9000"),
                LocalDateTime.of(2026, 5, 25, 10, 0));
    }

    private String writeRecord(ChargeIdempotencyRecord record) throws Exception {
        return objectMapper.writeValueAsString(record);
    }

    private ChargeIdempotencyRecord readRecord(String value) throws Exception {
        return objectMapper.readValue(value, ChargeIdempotencyRecord.class);
    }
}
