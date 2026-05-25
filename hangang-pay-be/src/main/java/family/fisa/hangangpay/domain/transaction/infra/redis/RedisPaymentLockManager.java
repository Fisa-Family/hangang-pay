package family.fisa.hangangpay.domain.transaction.infra.redis;

import family.fisa.hangangpay.domain.transaction.internal.PaymentLockManager;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class RedisPaymentLockManager implements PaymentLockManager {
    @Override
    public <T> T withTransactionLock(String transactionUuid, Supplier<T> supplier) {
        return null;
    }
}
