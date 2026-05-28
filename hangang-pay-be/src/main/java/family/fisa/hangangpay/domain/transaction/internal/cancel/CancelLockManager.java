package family.fisa.hangangpay.domain.transaction.internal.cancel;

import java.util.function.Supplier;

public interface CancelLockManager {

    // originalPaymentUuid 기준으로 락을 잡는다
    // cancelUuid는 CANCEL 레코드 생성 전에는 존재하지 않으므로 원본 PAYMENT UUID를 키로 사용
    <T> T withCancelLock(String originalPaymentUuid, Supplier<T> supplier);
}
