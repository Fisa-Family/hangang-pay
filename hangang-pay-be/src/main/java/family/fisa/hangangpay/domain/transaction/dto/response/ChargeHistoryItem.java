package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
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
        TransferStatus status,
        UserHistoryType historyType,
        LocalDateTime chargedAt)
        implements CursorItem {

    public static ChargeHistoryItem from(FundTransfer fundTransfer) {
        return ChargeHistoryItem.builder()
                .id(fundTransfer.getId())
                .amount(fundTransfer.getAmount())
                .discountAmount(fundTransfer.getDiscountAmount())
                .discountRate(fundTransfer.getDiscountRate())
                .status(fundTransfer.getStatus())
                .historyType(UserHistoryType.CHARGE)
                .chargedAt(fundTransfer.getCreatedAt())
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
