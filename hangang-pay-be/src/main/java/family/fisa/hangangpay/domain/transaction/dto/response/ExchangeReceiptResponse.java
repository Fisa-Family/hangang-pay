package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeReceiptResponse(
        Long transactionId,
        String transactionUuid,
        String txHash,
        String bankTransactionId,
        String status,
        BigDecimal amount,
        LocalDateTime confirmedAt) {

    public static ExchangeReceiptResponse from(
            Transaction transaction, BlockchainLedgerResponse blockchainLedger) {
        return new ExchangeReceiptResponse(
                transaction.getId(),
                transaction.getTransactionUuid(),
                transaction.getTxHash(),
                transaction.getBankTransactionId(),
                transaction.getStatus().name(),
                transaction.getAmount(),
                blockchainLedger != null ? blockchainLedger.confirmedAt() : null);
    }
}
