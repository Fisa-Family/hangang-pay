package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 충전 intent 생성 응답. */
public record ChargeIntentResponse(
        String transactionUuid,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal finalAmount,
        String accountNumber,
        String bankName,
        LocalDateTime expiresAt) {

    public static ChargeIntentResponse from(Transaction tx, LocalDateTime expiresAt) {
        BigDecimal discountAmount =
                tx.getDiscountAmount() != null ? tx.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalAmount =
                tx.getAmount() != null ? tx.getAmount().subtract(discountAmount) : BigDecimal.ZERO;
        return new ChargeIntentResponse(
                tx.getTransactionUuid(),
                tx.getStatus(),
                tx.getAmount(),
                finalAmount,
                tx.getFromAccount().getAccountNumber(),
                tx.getFromAccount().getInstitution().getInstitutionName(),
                expiresAt);
    }
}
