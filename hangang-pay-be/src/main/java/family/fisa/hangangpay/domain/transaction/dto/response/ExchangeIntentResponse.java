package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 환전 intent 생성 응답. */
public record ExchangeIntentResponse(
        String transactionUuid,
        TransactionStatus status,
        BigDecimal amount,
        String accountNumber,
        String bankName,
        LocalDateTime expiresAt) {

    public static ExchangeIntentResponse from(Transaction tx, LocalDateTime expiresAt) {
        return new ExchangeIntentResponse(
                tx.getTransactionUuid(),
                tx.getStatus(),
                tx.getAmount(),
                tx.getToAccount().getAccountNumber(),
                tx.getToAccount().getInstitution().getInstitutionName(),
                expiresAt);
    }
}
