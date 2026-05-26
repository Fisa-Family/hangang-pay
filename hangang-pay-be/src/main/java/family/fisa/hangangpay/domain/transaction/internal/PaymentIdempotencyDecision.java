package family.fisa.hangangpay.domain.transaction.internal;

import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;

public record PaymentIdempotencyDecision(
        PaymentIdempotencyDecisionType type, PaymentExecutionResponse responseSnapshot) {

    public static PaymentIdempotencyDecision newRequest() {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.NEW_REQUEST, null);
    }

    public static PaymentIdempotencyDecision returnSnapshot(
            PaymentExecutionResponse responseSnapshot) {
        return new PaymentIdempotencyDecision(
                PaymentIdempotencyDecisionType.RETURN_SNAPSHOT, responseSnapshot);
    }

    public static PaymentIdempotencyDecision processing() {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.PROCESSING, null);
    }

    public static PaymentIdempotencyDecision conflict() {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.CONFLICT, null);
    }
}
