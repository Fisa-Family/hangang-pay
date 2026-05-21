package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentHistoryItem(
        String paymentId,
        String merchantName,
        BigDecimal amount,
        LocalDateTime paidAt,
        TransactionStatus status,
        UserHistoryType historyType,
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
                UserHistoryType.PAYMENT,
                transaction.getCreatedAt(),
                transaction.getId());
    }
}
