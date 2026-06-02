package family.fisa.hangangpay.domain.transaction.internal.exchange;

import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;

/** 멱등 게이트 판정 결과. RETURN_SNAPSHOT일 때만 responseSnapshot이 채워진다. */
public record ExchangeIdempotencyDecision(
        ExchangeIdempotencyDecisionType type, ExchangeExecuteResponse responseSnapshot) {

    public static ExchangeIdempotencyDecision newRequest() {
        return new ExchangeIdempotencyDecision(ExchangeIdempotencyDecisionType.NEW_REQUEST, null);
    }

    public static ExchangeIdempotencyDecision returnSnapshot(
            ExchangeExecuteResponse responseSnapshot) {
        return new ExchangeIdempotencyDecision(
                ExchangeIdempotencyDecisionType.RETURN_SNAPSHOT, responseSnapshot);
    }

    public static ExchangeIdempotencyDecision alreadyFailed() {
        return new ExchangeIdempotencyDecision(
                ExchangeIdempotencyDecisionType.ALREADY_FAILED, null);
    }

    public static ExchangeIdempotencyDecision processing() {
        return new ExchangeIdempotencyDecision(ExchangeIdempotencyDecisionType.PROCESSING, null);
    }

    public static ExchangeIdempotencyDecision conflict() {
        return new ExchangeIdempotencyDecision(ExchangeIdempotencyDecisionType.CONFLICT, null);
    }
}
