package family.fisa.hangangpay.domain.transaction.internal;

/**
 * 멱등 게이트 판정 결과 (charge/payment/exchange/cancel 공용). RETURN_SNAPSHOT일 때만 responseSnapshot이 채워진다.
 *
 * @param <S> 플로우별 실행 응답 snapshot 타입
 */
public record IdempotencyDecision<S>(IdempotencyDecisionType type, S responseSnapshot) {

    /** 최초 요청 */
    public static <S> IdempotencyDecision<S> newRequest() {
        return new IdempotencyDecision<>(IdempotencyDecisionType.NEW_REQUEST, null);
    }

    /** 완료된 동일 요청 — 저장된 snapshot 재사용 */
    public static <S> IdempotencyDecision<S> returnSnapshot(S responseSnapshot) {
        return new IdempotencyDecision<>(IdempotencyDecisionType.RETURN_SNAPSHOT, responseSnapshot);
    }

    /** 이미 실패로 끝난 요청. snapshot은 없다(FAILED는 본문 응답이 아니라 예외로 내려감). */
    public static <S> IdempotencyDecision<S> alreadyFailed() {
        return new IdempotencyDecision<>(IdempotencyDecisionType.ALREADY_FAILED, null);
    }

    /** 기존 요청이 아직 처리 중 */
    public static <S> IdempotencyDecision<S> processing() {
        return new IdempotencyDecision<>(IdempotencyDecisionType.PROCESSING, null);
    }

    /** 같은 transactionUuid + 다른 requestHash 충돌 */
    public static <S> IdempotencyDecision<S> conflict() {
        return new IdempotencyDecision<>(IdempotencyDecisionType.CONFLICT, null);
    }
}
