package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.payment.entity.Payment;
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
    public static UserPaymentHistoryDetail from(Payment payment, BlockchainTx blockchainTx) {
        return UserPaymentHistoryDetail.builder()
                .historyId(payment.getId())
                .itemName(payment.getItemName())
                .amount(payment.getAmount())
                .approvalNumber(payment.getApprovalNumber())
                .paymentStatus(payment.getStatus().name())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .txHash(blockchainTx != null ? blockchainTx.getTxHash() : null)
                .blockchainStatus(blockchainTx != null ? blockchainTx.getStatus().name() : null)
                .build();
    }
}
