package family.fisa.hangangpay.domain.transaction.internal.cancel;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;

public record CancelIdempotencyDecision(
        CancelIdempotencyDecisionType type, PaymentCancelResponse responseSnapshot) {
    public static CancelIdempotencyDecision newRequest() {
        return new CancelIdempotencyDecision(CancelIdempotencyDecisionType.NEW_REQUEST, null);
    }

    public static CancelIdempotencyDecision returnSnapshot(PaymentCancelResponse responseSnapshot) {
        return new CancelIdempotencyDecision(
                CancelIdempotencyDecisionType.RETURN_SNAPSHOT, responseSnapshot);
    }

    public static CancelIdempotencyDecision processing() {
        return new CancelIdempotencyDecision(CancelIdempotencyDecisionType.PROCESSING, null);
    }

    public static CancelIdempotencyDecision conflict() {
        return new CancelIdempotencyDecision(CancelIdempotencyDecisionType.CONFLICT, null);
    }

    // 이미 실패로 끝난 취소 요청.
    public static CancelIdempotencyDecision alreadyFailed() {
        return new CancelIdempotencyDecision(CancelIdempotencyDecisionType.ALREADY_FAILED, null);
    }
}
