package family.fisa.hangangpay.domain.transaction.internal.charge;

import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyDecisionType;

/** 충전 멱등성 판단 결과 */
public record ChargeIdempotencyDecision(
        PaymentIdempotencyDecisionType type, // 판단 결과 타입
        ChargeExecuteResponse responseSnapshot) { // 재사용 응답 (RETURN_SNAPSHOT 시)

    /** 최초 요청 결정 생성 */
    public static ChargeIdempotencyDecision newRequest() {
        return new ChargeIdempotencyDecision(PaymentIdempotencyDecisionType.NEW_REQUEST, null);
    }

    /** 기존 snapshot 재사용 결정 생성 */
    public static ChargeIdempotencyDecision returnSnapshot(ChargeExecuteResponse responseSnapshot) {
        return new ChargeIdempotencyDecision(
                PaymentIdempotencyDecisionType.RETURN_SNAPSHOT, responseSnapshot);
    }

    /** 처리 중 상태 결정 생성 */
    public static ChargeIdempotencyDecision processing() {
        return new ChargeIdempotencyDecision(PaymentIdempotencyDecisionType.PROCESSING, null);
    }

    /** 요청 충돌 결정 생성 */
    public static ChargeIdempotencyDecision conflict() {
        return new ChargeIdempotencyDecision(PaymentIdempotencyDecisionType.CONFLICT, null);
    }
}
