package family.fisa.hangangpay.domain.transaction.infra.redis.cancel;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.infra.redis.AbstractRedisIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyStore;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCancelIdempotencyStore
        extends AbstractRedisIdempotencyStore<PaymentCancelResponse, CancelIdempotencyRecord>
        implements CancelIdempotencyStore {

    public RedisCancelIdempotencyStore(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    @Override
    protected String keyPrefix() {
        return "cancel:idempotency:";
    }

    @Override
    protected Class<CancelIdempotencyRecord> recordType() {
        return CancelIdempotencyRecord.class;
    }

    @Override
    protected BaseErrorCode notFoundError() {
        return TransactionErrorCode.CANCEL_IDEMPOTENCY_RECORD_NOT_FOUND;
    }

    @Override
    protected BaseErrorCode invalidError() {
        return TransactionErrorCode.CANCEL_IDEMPOTENCY_RECORD_INVALID;
    }

    @Override
    public IdempotencyDecision<PaymentCancelResponse> beginCancel(IdempotencyKey idempotencyKey) {
        return begin(
                idempotencyKey,
                CancelIdempotencyRecord.processing(
                        idempotencyKey.idempotencyId(), idempotencyKey.requestHash()));
    }

    @Override
    public void completeCancel(String originalPaymentUuid, PaymentCancelResponse responseSnapshot) {
        // SUCCESS snapshot 저장 — 이후 동일 요청 재시도에 그대로 반환
        String key = key(originalPaymentUuid);
        save(key, readRecord(key).complete(responseSnapshot));
    }

    @Override
    public void failCancel(String originalPaymentUuid) {
        // FAILED 마킹 + snapshot 제거. 이후 같은 원본결제 재취소 요청은 ALREADY_FAILED.
        String key = key(originalPaymentUuid);
        save(key, readRecord(key).fail());
    }
}
