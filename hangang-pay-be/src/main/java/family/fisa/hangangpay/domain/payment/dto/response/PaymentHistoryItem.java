package family.fisa.hangangpay.domain.payment.dto.response;

import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentHistoryItem(
        String paymentId,
        String merchantName,
        BigDecimal amount,
        LocalDateTime paidAt,
        PaymentStatus status,
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

    public static PaymentHistoryItem from(Payment payment, String merchantName) {
        return new PaymentHistoryItem(
                payment.getApprovalNumber(),
                merchantName,
                payment.getAmount(),
                payment.getCreatedAt(),
                payment.getStatus(),
                UserHistoryType.PAYMENT,
                payment.getCreatedAt(),
                payment.getId());
    }
}
