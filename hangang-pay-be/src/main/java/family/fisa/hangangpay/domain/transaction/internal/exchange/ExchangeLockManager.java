package family.fisa.hangangpay.domain.transaction.internal.exchange;

import java.util.function.Supplier;

/** 같은 transactionUuid 환전 실행/복구가 동시에 두 건 진입하지 못하게 막는 분산 락 포트. */
public interface ExchangeLockManager {

    <T> T withExchangeLock(String transactionUuid, Supplier<T> supplier);
}
