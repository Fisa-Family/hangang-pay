package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ChargeHistoryItem(
        Long id,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal discountRate,
        TransactionStatus status,
        UserHistoryType historyType,
        LocalDateTime chargedAt)
        implements CursorItem {

    public static ChargeHistoryItem from(Transaction transaction) {
        return ChargeHistoryItem.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .discountAmount(transaction.getDiscountAmount())
                .discountRate(transaction.getDiscountRate())
                .status(transaction.getStatus())
                .historyType(UserHistoryType.CHARGE)
                .chargedAt(transaction.getCreatedAt())
                .build();
    }

    @Override
    public LocalDateTime getCursorCreatedAt() {
        return chargedAt;
    }

    @Override
    public Long getCursorId() {
        return id;
    }
}
