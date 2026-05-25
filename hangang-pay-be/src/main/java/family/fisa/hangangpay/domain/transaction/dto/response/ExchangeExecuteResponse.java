package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ExchangeExecuteResponse(
        Long transactionId,
        String transactionUuid,
        BigDecimal amount,
        String accountNumber,
        String bankName,
        String txHash,
        TransactionStatus status,
        LocalDateTime exchangedAt) {
    public static ExchangeExecuteResponse from(Transaction tx) {
        return ExchangeExecuteResponse.builder()
                .transactionId(tx.getId())
                .transactionUuid(tx.getTransactionUuid())
                .amount(tx.getAmount())
                .accountNumber(tx.getToAccount().getAccountNumber())
                .bankName(tx.getToAccount().getInstitution().getInstitutionName())
                .txHash(tx.getTxHash())
                .status(tx.getStatus())
                .exchangedAt(tx.getUpdatedAt())
                .build();
    }
}
