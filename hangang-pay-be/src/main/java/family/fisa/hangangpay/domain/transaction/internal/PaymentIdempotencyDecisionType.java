package family.fisa.hangangpay.domain.transaction.internal;

public enum PaymentIdempotencyDecisionType {
    NEW_REQUEST,
    RETURN_SNAPSHOT,
    PROCESSING,
    CONFLICT
}
