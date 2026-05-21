package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCancelResponse(
        Long transactionId,
        String transactionUuid,
        String originalTransactionUuid,
        String approvalNumber,
        String txHash,
        String status,
        BigDecimal amount,
        LocalDateTime confirmedAt) {

    public static PaymentCancelResponse from(
            Transaction transaction, BlockchainLedgerResponse blockchainLedger) {
        return new PaymentCancelResponse(
                transaction.getId(),
                transaction.getTransactionUuid(),
                transaction.getOriginalTransactionUuid(),
                transaction.getApprovalNumber(),
                transaction.getTxHash(),
                transaction.getStatus().name(),
                transaction.getAmount(),
                blockchainLedger != null ? blockchainLedger.confirmedAt() : null);
    }
}
