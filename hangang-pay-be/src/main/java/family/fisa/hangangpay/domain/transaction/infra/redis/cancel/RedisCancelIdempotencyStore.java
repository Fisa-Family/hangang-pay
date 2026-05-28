package family.fisa.hangangpay.domain.transaction.infra.redis.cancel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyStore;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisCancelIdempotencyStore implements CancelIdempotencyStore {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final String KEY_PREFIX = "cancel:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public CancelIdempotencyDecision beginCancel(String originalPaymentUuid, String requestHash) {

        String key = KEY_PREFIX + originalPaymentUuid;

        // 1. 첫 요청은 PROCESSING 상태로 원자적 선점
        CancelIdempotencyRecord newRecord =
                CancelIdempotencyRecord.processing(originalPaymentUuid, requestHash);
        Boolean created =
                redisTemplate.opsForValue().setIfAbsent(key, serialize(newRecord), IDEMPOTENCY_TTL);

        if (Boolean.TRUE.equals(created)) {
            return CancelIdempotencyDecision.newRequest();
        }

        // 2. 기존 record 조회
        CancelIdempotencyRecord existing = readRecord(key);

        // 3. requestHash 불일치 — 같은 결제를 다른 주체가 취소하려는 충돌
        if (!existing.requestHash().equals(requestHash)) {
            return CancelIdempotencyDecision.conflict();
        }

        // 4. snapshot 있음 — 완료된 동일 요청, Bank 재호출 없이 저장된 응답 반환
        if (existing.responseSnapshot() != null) {
            return CancelIdempotencyDecision.returnSnapshot(existing.responseSnapshot());
        }

        // 5. snapshot 없음 — 기존 요청이 아직 처리 중
        return CancelIdempotencyDecision.processing();
    }

    @Override
    public void completeCancel(String originalPaymentUuid, PaymentCancelResponse responseSnapshot) {
        String key = KEY_PREFIX + originalPaymentUuid;
        CancelIdempotencyRecord existing = readRecord(key);

        // 6. SUCCESS snapshot 저장 — 이후 동일 요청 재시도에 그대로 반환
        redisTemplate
                .opsForValue()
                .set(key, serialize(existing.complete(responseSnapshot)), IDEMPOTENCY_TTL);
    }

    @Override
    public void markCancelStatus(String originalPaymentUuid, TransactionStatus status) {
        String key = KEY_PREFIX + originalPaymentUuid;
        CancelIdempotencyRecord existing = readRecord(key);

        // 7. UNKNOWN 등 — snapshot 없이 상태만 갱신, 복구 스케줄러가 처리하도록 유도
        redisTemplate
                .opsForValue()
                .set(key, serialize(existing.withStatus(status)), IDEMPOTENCY_TTL);
    }

    private CancelIdempotencyRecord readRecord(String key) {
        // Redis에서 JSON 문자열로 저장된 멱등성 record를 꺼낸다
        String value = redisTemplate.opsForValue().get(key);
        // null이면 completeCancel/markCancelStatus 호출 전에 record가 만료됐거나 저장 안 된 것 — 서버 내부 오류
        if (value == null) {
            throw new BusinessException(TransactionErrorCode.CANCEL_IDEMPOTENCY_RECORD_NOT_FOUND);
        }
        try {
            // JSON → CancelIdempotencyRecord 역직렬화
            return objectMapper.readValue(value, CancelIdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            // 역직렬화 실패는 Redis에 깨진 데이터가 저장된 것 — 서버 내부 오류
            throw new BusinessException(TransactionErrorCode.CANCEL_IDEMPOTENCY_RECORD_INVALID);
        }
    }

    private String serialize(CancelIdempotencyRecord record) {
        try {
            // CancelIdempotencyRecord → JSON 문자열로 직렬화해 Redis에 저장 가능한 형태로 변환
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new BusinessException(TransactionErrorCode.CANCEL_IDEMPOTENCY_RECORD_INVALID);
        }
    }
}
