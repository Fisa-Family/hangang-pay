package family.fisa.hangangpay.domain.transaction.internal.payment;

import java.util.Optional;

public interface PaymentIntentDedupStore {

    Optional<String> reserve(String fingerprint, String newTransactionUuid);
}
