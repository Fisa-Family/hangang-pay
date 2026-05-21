package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long transactionId,
        String transactionUuid,
        String approvalNumber,
        String txHash,
        String status,
        BigDecimal amount,
        LocalDateTime confirmedAt) {

    public static PaymentResponse from(
            Transaction transaction, BlockchainLedgerResponse blockchainLedger) {
        return new PaymentResponse(
                transaction.getId(),
                transaction.getTransactionUuid(),
                transaction.getApprovalNumber(),
                transaction.getTxHash(),
                transaction.getStatus().name(),
                transaction.getAmount(),
                blockchainLedger != null ? blockchainLedger.confirmedAt() : null);
    }
}
