package family.fisa.hangangpay.domain.transaction.internal;

import java.util.function.Supplier;

public interface PaymentLockManager {
    <T> T withTransactionLock(String transactionUuid, Supplier<T> supplier);
}
