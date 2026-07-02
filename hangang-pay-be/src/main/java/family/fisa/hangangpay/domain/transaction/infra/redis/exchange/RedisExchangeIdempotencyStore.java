package family.fisa.hangangpay.domain.transaction.infra.redis.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.infra.redis.AbstractRedisIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisExchangeIdempotencyStore
        extends AbstractRedisIdempotencyStore<ExchangeExecuteResponse, ExchangeIdempotencyRecord>
        implements ExchangeIdempotencyStore {

    public RedisExchangeIdempotencyStore(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    @Override
    protected String keyPrefix() {
        return "exchange:idempotency:";
    }

    @Override
    protected Class<ExchangeIdempotencyRecord> recordType() {
        return ExchangeIdempotencyRecord.class;
    }

    @Override
    protected BaseErrorCode notFoundError() {
        return TransactionErrorCode.EXCHANGE_IDEMPOTENCY_RECORD_NOT_FOUND;
    }

    @Override
    protected BaseErrorCode invalidError() {
        return TransactionErrorCode.EXCHANGE_IDEMPOTENCY_RECORD_INVALID;
    }

    @Override
    public IdempotencyDecision<ExchangeExecuteResponse> beginExecution(IdempotencyKey idempotencyKey) {
        // Bank 결과를 모르므로 status=UNKNOWN, snapshot=null 인 record로 선점한다.
        return begin(
                idempotencyKey,
                ExchangeIdempotencyRecord.inFlight(
                        idempotencyKey.idempotencyId(), idempotencyKey.requestHash()));
    }

    @Override
    public void completeExecution(
            String transactionUuid, ExchangeExecuteResponse responseSnapshot) {
        // 성공 상태 + 응답 snapshot으로 갱신해 덮어쓴다. 이후 동일 요청 재시도는 snapshot을 그대로 받는다.
        String key = key(transactionUuid);
        save(key, readRecord(key).complete(responseSnapshot));
    }

    @Override
    public void failExecution(String transactionUuid) {
        // FAILED로 마킹: 동일 transactionUuid 재시도는 '이미 실패'로 거절된다.
        String key = key(transactionUuid);
        save(key, readRecord(key).fail());
    }
}
