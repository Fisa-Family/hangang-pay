package family.fisa.hangangpay.domain.transaction.infra.redis.charge;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.infra.redis.AbstractRedisIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 충전 멱등성 판단 및 snapshot 저장 */
@Component
public class RedisChargeIdempotencyStore
        extends AbstractRedisIdempotencyStore<ChargeExecuteResponse, ChargeIdempotencyRecord>
        implements ChargeIdempotencyStore {

    public RedisChargeIdempotencyStore(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    @Override
    protected String keyPrefix() {
        return "charge:idempotency:";
    }

    @Override
    protected Class<ChargeIdempotencyRecord> recordType() {
        return ChargeIdempotencyRecord.class;
    }

    @Override
    protected BaseErrorCode notFoundError() {
        return TransactionErrorCode.CHARGE_IDEMPOTENCY_RECORD_NOT_FOUND;
    }

    @Override
    protected BaseErrorCode invalidError() {
        return TransactionErrorCode.CHARGE_IDEMPOTENCY_RECORD_INVALID;
    }

    /** 충전은 기존 동작상 FAILED 레코드 재요청도 진행 중(PROCESSING)으로 본다. */
    @Override
    protected boolean honorFailedState() {
        return false;
    }

    @Override
    public IdempotencyDecision<ChargeExecuteResponse> beginExecution(
            IdempotencyKey idempotencyKey, Long transactionId) {
        return begin(
                idempotencyKey,
                ChargeIdempotencyRecord.processing(
                        idempotencyKey.idempotencyId(),
                        idempotencyKey.requestHash(),
                        transactionId));
    }

    /** 완료 응답 snapshot을 Redis에 저장 */
    @Override
    public void completeExecution(String transactionUuid, ChargeExecuteResponse responseSnapshot) {
        String key = key(transactionUuid);
        save(key, readRecord(key).complete(responseSnapshot));
    }

    /** 상태만 교체하여 Redis 갱신 */
    @Override
    public void markExecutionStatus(String transactionUuid, TransactionStatus status) {
        String key = key(transactionUuid);
        save(key, readRecord(key).withStatus(status));
    }
}
