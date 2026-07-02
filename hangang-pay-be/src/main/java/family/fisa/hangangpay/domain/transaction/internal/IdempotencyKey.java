package family.fisa.hangangpay.domain.transaction.internal;

/**
 * 멱등성 요청 계약. 원자적 선점 키(idempotencyId)와 충돌 감지 해시(requestHash)를 항상 함께 다니는 한 쌍으로 묶는다.
 *
 * <p>idempotencyId는 charge/payment/exchange는 transactionUuid, cancel은 originalPaymentUuid.
 */
public record IdempotencyKey(String idempotencyId, String requestHash) {}
