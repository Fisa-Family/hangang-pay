package family.fisa.hangangpay.domain.transaction.internal.cancel;

import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;

public interface CancelIdempotencyStore {

    // 취소 요청 진입 시 멱등성 판정 — NEW_REQUEST / RETURN_SNAPSHOT / PROCESSING / CONFLICT
    CancelIdempotencyDecision beginCancel(String originalPaymentUuid, String requestHash);

    // Bank 취소 완료(SUCCESS/UNKNOWN) 후 최종 응답 snapshot 저장
    void completeCancel(String originalPaymentUuid, PaymentCancelResponse responseSnapshot);
}
