package family.fisa.hangangpay.domain.payment.dto.response;

import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserPaymentHistoryItem(
        String paymentId,
        String merchantName,
        BigDecimal amount,
        LocalDateTime paidAt,
        PaymentStatus status,
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

    public static UserPaymentHistoryItem from(Payment payment, String merchantName) {
        return new UserPaymentHistoryItem(
                payment.getApprovalNumber(),
                merchantName,
                payment.getAmount(),
                payment.getCreatedAt(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getId());
    }
}
