package family.fisa.hangangpay.domain.transaction.infra.redis.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyDecisionType;
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
class RedisExchangeIdempotencyStoreTest {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_HASH = "server-generated-request-hash";
    private static final String DIFFERENT_REQUEST_HASH = "different-request-hash";
    private static final String KEY = "exchange:idempotency:" + TRANSACTION_UUID;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisExchangeIdempotencyStore redisExchangeIdempotencyStore;

    @BeforeEach
    void setUp() {
        objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        redisExchangeIdempotencyStore =
                new RedisExchangeIdempotencyStore(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("기존 record가 없으면 UNKNOWN record를 선점하고 NEW_REQUEST를 반환한다")
    void beginExecution_noExistingRecordReturnsNewRequest() throws Exception {
        // setIfAbsent가 true면 이 요청이 transactionUuid의 첫 요청이다.
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(true);

        ExchangeIdempotencyDecision decision =
                redisExchangeIdempotencyStore.beginExecution(TRANSACTION_UUID, REQUEST_HASH);

        assertThat(decision.type()).isEqualTo(ExchangeIdempotencyDecisionType.NEW_REQUEST);
        assertThat(decision.responseSnapshot()).isNull();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        // 첫 요청 선점 시 Redis에는 snapshot 없이 UNKNOWN 상태만 저장한다.
        ExchangeIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(saved.responseSnapshot()).isNull();

        // 선점 성공 경로에서는 기존 record를 읽지 않는다.
        verify(valueOperations, never()).get(KEY);
    }

    @Test
    @DisplayName("기존 record와 requestHash가 다르면 CONFLICT를 반환한다")
    void beginExecution_differentHashReturnsConflict() throws Exception {
        ExchangeIdempotencyRecord existing = record(TransactionStatus.UNKNOWN, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        ExchangeIdempotencyDecision decision =
                redisExchangeIdempotencyStore.beginExecution(
                        TRANSACTION_UUID, DIFFERENT_REQUEST_HASH);

        // 같은 transactionUuid라도 requestHash가 다르면 같은 환전 재시도가 아니다.
        assertThat(decision.type()).isEqualTo(ExchangeIdempotencyDecisionType.CONFLICT);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("requestHash가 둘 다 null이면(현재 스텁) CONFLICT가 아니라 상태로 판정한다")
    void beginExecution_bothHashNullDoesNotConflict() throws Exception {
        // 운영 기본값: 해시 생성기가 null 스텁이라 둘 다 null → Objects.equals(null, null) = true →통과.
        ExchangeExecuteResponse snapshot = successSnapshot();
        ExchangeIdempotencyRecord existing = record(TransactionStatus.SUCCESS, null, snapshot);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        ExchangeIdempotencyDecision decision =
                redisExchangeIdempotencyStore.beginExecution(TRANSACTION_UUID, null);

        assertThat(decision.type()).isEqualTo(ExchangeIdempotencyDecisionType.RETURN_SNAPSHOT);
        assertThat(decision.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("같은 requestHash이고 상태가 SUCCESS면 RETURN_SNAPSHOT을 반환한다")
    void beginExecution_successReturnsSnapshot() throws Exception {
        ExchangeExecuteResponse snapshot = successSnapshot();
        ExchangeIdempotencyRecord existing =
                record(TransactionStatus.SUCCESS, REQUEST_HASH, snapshot);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        ExchangeIdempotencyDecision decision =
                redisExchangeIdempotencyStore.beginExecution(TRANSACTION_UUID, REQUEST_HASH);

        // 완료된 동일 요청은 Bank를 다시 호출하지 않도록 저장된 응답을 돌려준다.

        assertThat(decision.type()).isEqualTo(ExchangeIdempotencyDecisionType.RETURN_SNAPSHOT);
        assertThat(decision.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("같은 requestHash이고 상태가 FAILED면 ALREADY_FAILED를 반환한다")
    void beginExecution_failedReturnsAlreadyFailed() throws Exception {
        ExchangeIdempotencyRecord existing = record(TransactionStatus.FAILED, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        ExchangeIdempotencyDecision decision =
                redisExchangeIdempotencyStore.beginExecution(TRANSACTION_UUID, REQUEST_HASH);

        // 실패로 끝난 요청은 새 시도를 유도하기 위해 거절한다.

        assertThat(decision.type()).isEqualTo(ExchangeIdempotencyDecisionType.ALREADY_FAILED);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("같은 requestHash이고 상태가 UNKNOWN이면 PROCESSING을 반환한다")
    void beginExecution_unknownReturnsProcessing() throws Exception {
        ExchangeIdempotencyRecord existing = record(TransactionStatus.UNKNOWN, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        ExchangeIdempotencyDecision decision =
                redisExchangeIdempotencyStore.beginExecution(TRANSACTION_UUID, REQUEST_HASH);

        // 아직 결과가 확정되지 않은 진행 중 요청은 중복 실행을 막기 위해 거절한다.

        assertThat(decision.type()).isEqualTo(ExchangeIdempotencyDecisionType.PROCESSING);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("completeExecution은 기존 record를 SUCCESS + snapshot으로 갱신한다")
    void completeExecution_storesSnapshot() throws Exception {
        ExchangeIdempotencyRecord existing = record(TransactionStatus.UNKNOWN, REQUEST_HASH, null);
        ExchangeExecuteResponse snapshot = successSnapshot();

        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        redisExchangeIdempotencyStore.completeExecution(TRANSACTION_UUID, snapshot);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        ExchangeIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("failExecution은 기존 record를 FAILED로 마킹한다")
    void failExecution_marksFailed() throws Exception {
        ExchangeIdempotencyRecord existing = record(TransactionStatus.UNKNOWN, REQUEST_HASH, null);

        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        redisExchangeIdempotencyStore.failExecution(TRANSACTION_UUID);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        ExchangeIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.responseSnapshot()).isNull();
    }

    private ExchangeIdempotencyRecord record(
            TransactionStatus status,
            String requestHash,
            ExchangeExecuteResponse responseSnapshot) {
        return new ExchangeIdempotencyRecord(
                TRANSACTION_UUID, requestHash, status, responseSnapshot);
    }

    private ExchangeExecuteResponse successSnapshot() {
        return ExchangeExecuteResponse.builder()
                .transactionId(123L)
                .transactionUuid(TRANSACTION_UUID)
                .amount(new BigDecimal("10000"))
                .accountNumber("110-1234-5678")
                .bankName("한강은행")
                .txHash("0x-snapshot")
                .status(TransactionStatus.SUCCESS)
                .exchangedAt(LocalDateTime.of(2026, 5, 25, 10, 0))
                .build();
    }

    private String writeRecord(ExchangeIdempotencyRecord record) throws Exception {
        return objectMapper.writeValueAsString(record);
    }

    private ExchangeIdempotencyRecord readRecord(String value) throws Exception {
        return objectMapper.readValue(value, ExchangeIdempotencyRecord.class);
    }
}
