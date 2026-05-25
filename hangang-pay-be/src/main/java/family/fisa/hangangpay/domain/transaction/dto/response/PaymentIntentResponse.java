package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentIntentResponse(
    String transactionUuid,
    TransactionStatus status,
    Long merchantPartyId,
    String merchantName,
    BigDecimal amount,
    String itemName,
    LocalDateTime expiresAt) {

    public static PaymentIntentResponse from(Transaction t, Merchant m, LocalDateTime expiresAt) {
        return new PaymentIntentResponse(
            t.getTransactionUuid(),
            t.getStatus(),
            m.getParty().getId(),
            m.getMerchantName(),
            t.getAmount(),
            t.getItemName(),
            expiresAt);
    }
}
