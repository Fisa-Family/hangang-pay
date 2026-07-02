package family.fisa.hangangpay.domain.transaction.infra.redis.cancel;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.infra.redis.AbstractRedisLockManager;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelLockManager;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * originalPaymentUuid 기준으로 취소 실행 락을 잡는다. cancelUuid는 CANCEL 레코드 생성 전에는 존재하지 않으므로 원본 PAYMENT UUID를
 * 키로 사용한다.
 */
@Component
public class RedisCancelLockManager extends AbstractRedisLockManager implements CancelLockManager {

    public RedisCancelLockManager(StringRedisTemplate redisTemplate) {
        super(redisTemplate);
    }

    @Override
    protected String keyPrefix() {
        return "cancel:lock:";
    }

    @Override
    protected BaseErrorCode alreadyProcessingError() {
        return TransactionErrorCode.CANCEL_ALREADY_PROCESSING;
    }

    @Override
    public <T> T withCancelLock(String originalPaymentUuid, Supplier<T> supplier) {
        return runWithLock(originalPaymentUuid, supplier);
    }
}
