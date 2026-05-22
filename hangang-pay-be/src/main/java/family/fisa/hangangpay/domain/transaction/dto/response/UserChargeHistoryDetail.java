package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record UserChargeHistoryDetail(
        Long historyId,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal discountRate,
        BigDecimal actualPaidAmount,
        String transferStatus,
        String transferType,
        String accountNumber,
        String bankName,
        String walletAddress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String txHash,
        String blockchainStatus) {

    public static UserChargeHistoryDetail from(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        BigDecimal discountAmountOrZero =
                transaction.getDiscountAmount() != null
                        ? transaction.getDiscountAmount()
                        : BigDecimal.ZERO;
        BigDecimal discountRatePercent =
                transaction.getDiscountRate() != null
                        ? transaction.getDiscountRate().multiply(BigDecimal.valueOf(100))
                        : null;
        BigDecimal actualPaid = amount.subtract(discountAmountOrZero);

        return UserChargeHistoryDetail.builder()
                .historyId(transaction.getId())
                .amount(amount)
                .discountAmount(transaction.getDiscountAmount())
                .discountRate(discountRatePercent)
                .actualPaidAmount(actualPaid)
                .transferStatus(transaction.getStatus().name())
                .transferType(transaction.getTransactionType().name())
                .accountNumber(transaction.getFromAccount().getAccountNumber())
                .bankName(transaction.getFromAccount().getInstitution().getInstitutionName())
                .walletAddress(transaction.getToWallet().getAddress())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .txHash(transaction.getTxHash())
                .blockchainStatus(transaction.getStatus().name())
                .build();
    }
}
