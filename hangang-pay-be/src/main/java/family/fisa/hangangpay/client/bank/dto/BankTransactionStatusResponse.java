package family.fisa.hangangpay.client.bank.dto;

import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;

public record BankTransactionStatusResponse(
        String transactionUuid,
        Long bankTransactionId,
        TransactionStatus status,
        String txHash,
        LocalDateTime confirmedAt) {

    public static BankTransactionStatusResponse failed(String recoveryUuid) {
        return new BankTransactionStatusResponse(
                recoveryUuid, null, TransactionStatus.FAILED, null, null);
    }
}
