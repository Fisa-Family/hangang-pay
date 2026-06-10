package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCancelResponse(
        String transactionUuid,
        TransactionStatus status, // SUCCESS / UNKNOWN — 클라이언트가 확정 여부를 판단하는 기준
        String approvalNumber,
        BigDecimal amount,
        LocalDateTime confirmedAt) {
    public static PaymentCancelResponse from(Transaction t, LocalDateTime confirmedAt) {
        return new PaymentCancelResponse(
                t.getTransactionUuid(),
                t.getStatus(),
                t.getApprovalNumber(),
                t.getAmount(),
                confirmedAt);
    }
}
