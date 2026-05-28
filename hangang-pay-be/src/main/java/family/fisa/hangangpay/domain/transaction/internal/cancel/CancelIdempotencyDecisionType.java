package family.fisa.hangangpay.domain.transaction.internal.cancel;

public enum CancelIdempotencyDecisionType {
    NEW_REQUEST,
    RETURN_SNAPSHOT,
    PROCESSING,
    CONFLICT
}
