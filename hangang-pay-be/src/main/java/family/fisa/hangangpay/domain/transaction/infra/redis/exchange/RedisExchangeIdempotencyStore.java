package family.fisa.hangangpay.domain.transaction.infra.redis.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisExchangeIdempotencyStore implements ExchangeIdempotencyStore {

    // 멱등키 보관 기간
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    // 충돌 방지용
    private static final String KEY_PREFIX = "exchange:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ExchangeIdempotencyDecision beginExecution(String transactionUuid, String requestHash) {
        // 1. redis에 저장할 키 문자열 준비
        String key = key(transactionUuid);

        // 2. Redis에 넣을 선점용 값 준비
        // Bank의 결과를 모르므로 status=UNKNOWN, snapsho=null 인 record
        ExchangeIdempotencyRecord newRecord =
                ExchangeIdempotencyRecord.inFlight(transactionUuid, requestHash);

        // 3. 원자적 선점: SET key value NX PX(7d)
        // - 키가 없을 때만 (NX) 저장하고 true 반환
        // - 키가 이미 있으면 아무것도 안 하고 false 반환
        Boolean created =
                redisTemplate.opsForValue().setIfAbsent(key, serialize(newRecord), IDEMPOTENCY_TTL);

        // 4. 선정 성공 -> 첫 요청이므로 정상 진행 알림
        if (Boolean.TRUE.equals(created)) {
            return ExchangeIdempotencyDecision.newRequest();
        }

        // 5. 선정실패 -> 기존 record를 꺼내 어떤 상태인지 판정
        return decideForExisting(requestHash, key);
    }

    /** 선점 실패 시: 기존 record를 꺼내 CONFLICT / SNAPSHOT / FAILED / PROCESSING 중 하나로 판정 */
    private ExchangeIdempotencyDecision decideForExisting(String requestHash, String key) {
        ExchangeIdempotencyRecord existing = readRecord(key);

        // 6. transactionUuid는 같지만 요청 내용(requestHash)이 다르면 같은 환전 재시도 X
        if (!Objects.equals(existing.requestHash(), requestHash)) {
            return ExchangeIdempotencyDecision.conflict();
        }

        // 7. requestHash가 같을 경우 기존 record 로 판정
        return switch (existing.status()) {
            // 완료된 동일 요청은 Bank를 다시 호출하지 않고 저장된 응답 snapshot을 재사용
            case SUCCESS -> ExchangeIdempotencyDecision.returnSnapshot(existing.responseSnapshot());
            // 실패로 끝난 요청은 새로 시도하도록 거절
            case FAILED -> ExchangeIdempotencyDecision.alreadyFailed();
            // 그 외(UNKNOWN)는 아직 진행 중이라는 의미
            default -> ExchangeIdempotencyDecision.processing();
        };
    }

    @Override
    public void completeExecution(
            String transactionUuid, ExchangeExecuteResponse responseSnapshot) {

        // 1. key 준비 후, begin 단계에서 선점해둔 기존 record 꺼냄
        String key = key(transactionUuid);
        ExchangeIdempotencyRecord existing = readRecord(key);

        // 2. 기존 record 성공 상태 + 응답 snapshot으로 갱신해 덮어 쓴다
        // 이후 동일 transactionUuid 재시도는 snapshot을 그대로 받는다.
        redisTemplate
                .opsForValue()
                .set(key, serialize(existing.complete(responseSnapshot)), IDEMPOTENCY_TTL);
    }

    @Override
    public void failExecution(String transactionUuid) {
        // 1. key 준비 후, 기존 record를 꺼낸다
        String key = key(transactionUuid);
        ExchangeIdempotencyRecord existing = readRecord(key);

        // 2. FAILED로 마킹: 동일 transactionUuid 재시도는 '이미 실패'로 거절된다.
        redisTemplate.opsForValue().set(key, serialize(existing.fail()), IDEMPOTENCY_TTL);
    }

    /** prefix를 붙인 KEY 생성 ex) exchange:idempotency:{transactionUuid} */
    private String key(String transactionUuid) {
        return KEY_PREFIX + transactionUuid;
    }

    private ExchangeIdempotencyRecord readRecord(String key) {
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            throw new BusinessException(TransactionErrorCode.EXCHANGE_IDEMPOTENCY_RECORD_NOT_FOUND);
        }

        try {
            return objectMapper.readValue(value, ExchangeIdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(TransactionErrorCode.EXCHANGE_IDEMPOTENCY_RECORD_INVALID);
        }
    }

    private String serialize(ExchangeIdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new BusinessException(TransactionErrorCode.EXCHANGE_IDEMPOTENCY_RECORD_INVALID);
        }
    }
}
