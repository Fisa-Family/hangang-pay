package family.fisa.hangangpay.domain.transaction.internal.charge;

import java.util.function.Supplier;

/** 같은 transactionUuid 충전 실행이 동시에 두 건 진입하지 못하게 막는 분산 락 포트. */
public interface ChargeLockManager {

    <T> T withChargeLock(String transactionUuid, Supplier<T> supplier);
}
