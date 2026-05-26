package family.fisa.hangangpay.domain.transaction.internal;

import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;

public interface PaymentIdempotencyStore {
    PaymentIdempotencyDecision beginExecution(
            String transactionUuid, String requestHash, Long transactionId);

    void completeExecution(String transactionUuid, PaymentExecutionResponse responseSnapshot);

    void markExecutionStatus(String transactionUuid, TransactionStatus status);
}
