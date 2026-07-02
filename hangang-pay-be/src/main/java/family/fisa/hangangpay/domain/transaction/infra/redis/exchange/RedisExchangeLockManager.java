package family.fisa.hangangpay.domain.transaction.infra.redis.exchange;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.infra.redis.AbstractRedisLockManager;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeLockManager;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 같은 transactionUuid 환전 실행/복구 흐름이 동시에 두 개 들어가지 못하게 막는다. */
@Component
public class RedisExchangeLockManager extends AbstractRedisLockManager implements ExchangeLockManager {

    public RedisExchangeLockManager(StringRedisTemplate redisTemplate) {
        super(redisTemplate);
    }

    @Override
    protected String keyPrefix() {
        return "exchange:lock:";
    }

    @Override
    protected BaseErrorCode alreadyProcessingError() {
        return TransactionErrorCode.EXCHANGE_IN_PROGRESS;
    }

    @Override
    public <T> T withExchangeLock(String transactionUuid, Supplier<T> supplier) {
        return runWithLock(transactionUuid, supplier);
    }
}
