package family.fisa.hangangpay.domain.transaction.internal.payment;

import java.util.function.Supplier;

public interface PaymentLockManager {

    <T> T withTransactionLock(String transactionUuid, Supplier<T> supplier);
}
