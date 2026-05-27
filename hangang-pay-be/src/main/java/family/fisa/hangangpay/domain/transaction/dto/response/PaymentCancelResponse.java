package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCancelResponse(
        String transactionUuid,
        String approvalNumber,
        String txHash,
        BigDecimal amount,
        LocalDateTime confirmedAt
) {
    public static PaymentCancelResponse from (Transaction t, LocalDateTime confirmedAt) {
        return new PaymentCancelResponse (
                t.getTransactionUuid(),
                t.getApprovalNumber(),
                t.getTxHash(),
                t.getAmount(),
                confirmedAt
        );
    }
}
