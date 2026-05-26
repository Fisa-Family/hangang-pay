package family.fisa.hangangpay.client.bank.dto;

import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;

public record BankTransactionStatusResponse(
        String transactionUuid,
        TransactionStatus status,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt) {}
