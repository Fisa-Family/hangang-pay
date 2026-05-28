package family.fisa.hangangpay.domain.transaction.internal.cancel;

import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;

public interface CancelIdempotencyStore {

    // 취소 요청 진입 시 멱등성 판정 — NEW_REQUEST / RETURN_SNAPSHOT / PROCESSING / CONFLICT
    CancelIdempotencyDecision beginCancel(String originalPaymentUuid, String requestHash);

    // Bank 취소 성공 후 최종 응답 snapshot 저장
    void completeCancel(String originalPaymentUuid, PaymentCancelResponse responseSnapshot);

    // Bank 타임아웃(UNKNOWN) 등 snapshot 없이 상태만 갱신
    void markCancelStatus(String originalPaymentUuid, TransactionStatus status);
}
