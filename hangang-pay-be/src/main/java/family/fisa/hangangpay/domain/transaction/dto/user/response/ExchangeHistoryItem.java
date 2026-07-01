package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ExchangeHistoryItem(
        Long id,
        BigDecimal amount,
        TransactionStatus status,
        UserHistoryType historyType,
        LocalDateTime exchangedAt)
        implements CursorItem {

    public static ExchangeHistoryItem from(Transaction transaction) {
        return ExchangeHistoryItem.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .historyType(UserHistoryType.EXCHANGE)
                .exchangedAt(transaction.getCreatedAt())
                .build();
    }

    @Override
    public LocalDateTime getCursorCreatedAt() {
        return exchangedAt;
    }

    @Override
    public Long getCursorId() {
        return id;
    }
}
