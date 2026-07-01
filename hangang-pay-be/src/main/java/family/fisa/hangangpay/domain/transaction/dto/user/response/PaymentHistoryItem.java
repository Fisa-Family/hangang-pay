package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentHistoryItem(
        String paymentId,
        String merchantName,
        BigDecimal amount,
        LocalDateTime paidAt,
        TransactionStatus status,
        TransactionType historyType,
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

    public static PaymentHistoryItem from(Transaction transaction, String merchantName) {
        return new PaymentHistoryItem(
                transaction.getApprovalNumber(),
                merchantName,
                transaction.getAmount(),
                transaction.getCreatedAt(),
                transaction.getStatus(),
                transaction.getTransactionType(),
                transaction.getCreatedAt(),
                transaction.getId());
    }
}
