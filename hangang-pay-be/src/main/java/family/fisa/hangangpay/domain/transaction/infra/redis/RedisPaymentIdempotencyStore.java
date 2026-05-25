package family.fisa.hangangpay.domain.transaction.infra.redis;

import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.PaymentIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.PaymentIdempotencyStore;
import org.springframework.stereotype.Component;

@Component
public class RedisPaymentIdempotencyStore implements PaymentIdempotencyStore {
    @Override
    public PaymentIdempotencyDecision beginExecution(String transactionUuid, String requestHash, Long transactionId) {
        return null;
    }

    @Override
    public void completeExecution(String transactionUuid, PaymentExecutionResponse responseSnapshot) {

    }

    @Override
    public void markExecutionStatus(String transactionUuid, TransactionStatus status) {

    }
}
