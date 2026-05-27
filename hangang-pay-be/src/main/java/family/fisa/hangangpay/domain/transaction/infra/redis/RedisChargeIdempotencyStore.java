package family.fisa.hangangpay.domain.transaction.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.ChargeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.ChargeIdempotencyStore;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 충전 멱등성 판단 및 snapshot 저장 */
@Component
@RequiredArgsConstructor
public class RedisChargeIdempotencyStore implements ChargeIdempotencyStore {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final String KEY_PREFIX = "charge:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ChargeIdempotencyDecision beginExecution(
            String transactionUuid, String requestHash, Long transactionId) {

        String key = key(transactionUuid);

        // 첫 실행 요청을 원자적으로 선점
        ChargeIdempotencyRecord newRecord =
                ChargeIdempotencyRecord.processing(transactionUuid, requestHash, transactionId);

        Boolean created =
                redisTemplate.opsForValue().setIfAbsent(key, serialize(newRecord), IDEMPOTENCY_TTL);

        if (Boolean.TRUE.equals(created)) {
            return ChargeIdempotencyDecision.newRequest();
        }

        ChargeIdempotencyRecord existing = readRecord(key);

        // 같은 UUID + 다른 requestHash → 충돌
        if (!existing.requestHash().equals(requestHash)) {
            return ChargeIdempotencyDecision.conflict();
        }

        // 완료된 동일 요청 → snapshot 재사용
        if (existing.responseSnapshot() != null) {
            return ChargeIdempotencyDecision.returnSnapshot(existing.responseSnapshot());
        }

        // snapshot 없음 → 이전 요청이 아직 처리 중
        return ChargeIdempotencyDecision.processing();
    }

    /** 완료 응답 snapshot을 Redis에 저장 */
    @Override
    public void completeExecution(String transactionUuid, ChargeExecuteResponse responseSnapshot) {
        String key = key(transactionUuid);
        ChargeIdempotencyRecord existing = readRecord(key);
        ChargeIdempotencyRecord completed = existing.complete(responseSnapshot);
        redisTemplate.opsForValue().set(key, serialize(completed), IDEMPOTENCY_TTL);
    }

    /** 상태만 교체하여 Redis 갱신 */
    @Override
    public void markExecutionStatus(String transactionUuid, TransactionStatus status) {
        String key = key(transactionUuid);
        ChargeIdempotencyRecord existing = readRecord(key);
        ChargeIdempotencyRecord updated = existing.withStatus(status);
        redisTemplate.opsForValue().set(key, serialize(updated), IDEMPOTENCY_TTL);
    }

    /** 충전 멱등성 Redis 키 반환 */
    private String key(String transactionUuid) {
        return KEY_PREFIX + transactionUuid;
    }

    /** Redis에서 레코드 역직렬화 후 반환 */
    private ChargeIdempotencyRecord readRecord(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new BusinessException(TransactionErrorCode.CHARGE_IDEMPOTENCY_RECORD_NOT_FOUND);
        }
        try {
            return objectMapper.readValue(value, ChargeIdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(TransactionErrorCode.CHARGE_IDEMPOTENCY_RECORD_INVALID);
        }
    }

    /** 레코드 JSON 직렬화 후 반환 */
    private String serialize(ChargeIdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new BusinessException(TransactionErrorCode.CHARGE_IDEMPOTENCY_RECORD_INVALID);
        }
    }
}
