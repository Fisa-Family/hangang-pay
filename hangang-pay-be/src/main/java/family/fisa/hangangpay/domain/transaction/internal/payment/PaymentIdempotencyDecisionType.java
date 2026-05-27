package family.fisa.hangangpay.domain.transaction.internal.payment;

public enum PaymentIdempotencyDecisionType {
    NEW_REQUEST,
    RETURN_SNAPSHOT,
    PROCESSING,
    CONFLICT
}
