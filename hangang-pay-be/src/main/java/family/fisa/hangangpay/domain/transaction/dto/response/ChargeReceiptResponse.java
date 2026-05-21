package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeReceiptResponse(
        Long transactionId,
        String transactionUuid,
        String txHash,
        String bankTransactionId,
        String status,
        BigDecimal amount,
        BigDecimal discountAmount,
        LocalDateTime confirmedAt) {

    public static ChargeReceiptResponse from(
            Transaction transaction, BlockchainLedgerResponse blockchainLedger) {
        return new ChargeReceiptResponse(
                transaction.getId(),
                transaction.getTransactionUuid(),
                transaction.getTxHash(),
                transaction.getBankTransactionId(),
                transaction.getStatus().name(),
                transaction.getAmount(),
                transaction.getDiscountAmount(),
                blockchainLedger != null ? blockchainLedger.confirmedAt() : null);
    }
}
