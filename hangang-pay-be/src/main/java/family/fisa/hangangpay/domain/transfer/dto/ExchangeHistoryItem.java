package family.fisa.hangangpay.domain.transfer.dto;

import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ExchangeHistoryItem(
        Long id,
        BigDecimal amount,
        TransferStatus status,
        UserHistoryType historyType,
        LocalDateTime exchangedAt)
        implements CursorItem {

    public static ExchangeHistoryItem from(FundTransfer fundTransfer) {
        return ExchangeHistoryItem.builder()
                .id(fundTransfer.getId())
                .amount(fundTransfer.getAmount())
                .status(fundTransfer.getStatus())
                .historyType(UserHistoryType.EXCHANGE)
                .exchangedAt(fundTransfer.getCreatedAt())
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
