package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;

public interface PaymentIdempotencyStore {
    PaymentIdempotencyDecision beginExecution(
            String transactionUuid, String requestHash, Long transactionId);

    // Bank 결제 완료(SUCCESS/UNKNOWN) 후 최종 응답 snapshot 저장
    void completeExecution(String transactionUuid, PaymentExecutionResponse responseSnapshot);
}
