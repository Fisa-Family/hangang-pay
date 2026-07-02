package family.fisa.hangangpay.domain.transaction.infra.redis.charge;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.infra.redis.AbstractRedisLockManager;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeLockManager;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 같은 transactionUuid 충전 실행 흐름이 동시에 두 개 들어가지 못하게 막는다. */
@Component
public class RedisChargeLockManager extends AbstractRedisLockManager implements ChargeLockManager {

    public RedisChargeLockManager(StringRedisTemplate redisTemplate) {
        super(redisTemplate);
    }

    @Override
    protected String keyPrefix() {
        return "charge:lock:";
    }

    @Override
    protected BaseErrorCode alreadyProcessingError() {
        return TransactionErrorCode.CHARGE_ALREADY_PROCESSING;
    }

    @Override
    public <T> T withChargeLock(String transactionUuid, Supplier<T> supplier) {
        return runWithLock(transactionUuid, supplier);
    }
}
