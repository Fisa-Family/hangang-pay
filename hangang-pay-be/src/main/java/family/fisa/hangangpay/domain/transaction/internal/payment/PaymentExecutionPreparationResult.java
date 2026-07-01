package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecutionResponse;

public record PaymentExecutionPreparationResult(
        PaymentExecutionPrepared prepared, PaymentExecutionResponse responseSnapshot) {

    public static PaymentExecutionPreparationResult prepared(PaymentExecutionPrepared prepared) {
        return new PaymentExecutionPreparationResult(prepared, null);
    }

    public static PaymentExecutionPreparationResult snapshot(PaymentExecutionResponse snapshot) {
        return new PaymentExecutionPreparationResult(null, snapshot);
    }

    public boolean hasSnapshot() {
        return responseSnapshot != null;
    }
}
