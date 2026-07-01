package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record UserPaymentHistoryDetail(
        Long historyId,
        String itemName,
        BigDecimal amount,
        String approvalNumber,
        String paymentStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String txHash,
        String blockchainStatus) {

    public static UserPaymentHistoryDetail from(Transaction transaction) {
        return UserPaymentHistoryDetail.builder()
                .historyId(transaction.getId())
                .itemName(transaction.getItemName())
                .amount(transaction.getAmount())
                .approvalNumber(transaction.getApprovalNumber())
                .paymentStatus(transaction.getStatus().name())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .txHash(transaction.getTxHash())
                .blockchainStatus(transaction.getStatus().name())
                .build();
    }
}
