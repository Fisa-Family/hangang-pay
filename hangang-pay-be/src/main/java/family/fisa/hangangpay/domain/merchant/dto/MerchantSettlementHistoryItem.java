package family.fisa.hangangpay.domain.merchant.dto;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record MerchantSettlementHistoryItem(
        Long settlementId,
        BigDecimal amount,
        TransactionStatus settlementStatus,
        String settlementStatusText,
        LocalDateTime requestedAt,
        LocalDateTime completedAt)
        implements CursorItem {

    public static MerchantSettlementHistoryItem from(Transaction transaction) {
        return MerchantSettlementHistoryItem.builder()
                .settlementId(transaction.getId())
                .amount(transaction.getAmount())
                .settlementStatus(transaction.getStatus())
                .settlementStatusText(toStatusText(transaction.getStatus()))
                .requestedAt(transaction.getCreatedAt())
                .completedAt(transaction.getUpdatedAt())
                .build();
    }

    private static String toStatusText(TransactionStatus status) {
        return switch (status) {
            case SUCCESS -> "정산 완료";
            case PENDING -> "정산 대기";
            case FAILED -> "정산 실패";
        };
    }

    @Override
    public LocalDateTime getCursorCreatedAt() {
        return requestedAt;
    }

    @Override
    public Long getCursorId() {
        return settlementId;
    }
}
