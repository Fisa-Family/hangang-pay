package family.fisa.hangangpay.domain.transaction.internal.cancel;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;

public interface CancelIdempotencyStore {

    // 취소 요청 진입 시 멱등성 판정 — NEW_REQUEST / RETURN_SNAPSHOT / PROCESSING / CONFLICT
    IdempotencyDecision<PaymentCancelResponse> beginCancel(IdempotencyKey idempotencyKey);

    // Bank 취소 완료(SUCCESS/UNKNOWN) 후 최종 응답 snapshot 저장
    void completeCancel(String originalPaymentUuid, PaymentCancelResponse responseSnapshot);

    // Bank 취소가 결정적으로 실패했을 때 record를 FAILED로 마킹한다.
    // 이후 동일 원본결제 재취소 요청은 ALREADY_FAILED로 즉시 거절된다.
    void failCancel(String originalPaymentUuid);
}
