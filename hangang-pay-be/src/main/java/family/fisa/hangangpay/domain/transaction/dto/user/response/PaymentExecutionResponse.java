package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentExecutionResponse(
        String transactionUuid,
        TransactionStatus status,
        String approvalNumber,
        BigDecimal amount,
        String merchantName,
        LocalDateTime confirmedAt) {

    public static PaymentExecutionResponse from(
            Transaction t, String merchantName, LocalDateTime confirmedAt) {
        return new PaymentExecutionResponse(
                t.getTransactionUuid(),
                t.getStatus(),
                t.getApprovalNumber(),
                t.getAmount(),
                merchantName,
                confirmedAt);
    }
}
