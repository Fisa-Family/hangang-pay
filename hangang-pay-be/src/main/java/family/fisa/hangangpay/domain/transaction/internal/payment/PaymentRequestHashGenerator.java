package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestHashGenerator {
    public String generatePaymentExecuteHash(Transaction transaction) {
        return null;
    }

    //    public String generateCancelExecuteHash(Transaction transaction) {
    //        return null;
    //    }
    //
    //    public String generateExchangeExecuteHash(Transaction transaction) {
    //        return null;
    //    }
    //
    //    public String generateChargeExecuteHash(Transaction transaction) {
    //        return null;
    //    }

}
