package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantPaymentHistoryItem(
        String paymentId,
        String payerName,
        BigDecimal amount,
        TransactionType transactionType,
        LocalDateTime createdAt,
        LocalDateTime cursorCreatedAt,
        Long cursorId)
        implements CursorItem {

    @Override
    public LocalDateTime getCursorCreatedAt() {
        return cursorCreatedAt;
    }

    @Override
    public Long getCursorId() {
        return cursorId;
    }

    public static MerchantPaymentHistoryItem from(Transaction transaction, String payerName) {
        return new MerchantPaymentHistoryItem(
                transaction.getApprovalNumber(),
                payerName,
                transaction.getAmount(),
                transaction.getTransactionType(),
                transaction.getCreatedAt(),
                transaction.getCreatedAt(),
                transaction.getId());
    }
}
