package family.fisa.hangangpay.domain.transfer.dto.response;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
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
    String blockchainStatus
) {
    public static UserChargeHistoryDetail from(FundTransfer fundTransfer, BlockchainTx blockchainTx) {
        BigDecimal amount = fundTransfer.getAmount();
        BigDecimal discountAmountOrZero =
            fundTransfer.getDiscountAmount() != null
                ? fundTransfer.getDiscountAmount()
                : BigDecimal.ZERO;
        BigDecimal discountRatePercent =
            fundTransfer.getDiscountRate() != null
                ? fundTransfer.getDiscountRate().multiply(BigDecimal.valueOf(100))
                : null;
        BigDecimal actualPaid = amount.subtract(discountAmountOrZero);

        return UserChargeHistoryDetail.builder()
                                      .historyId(fundTransfer.getId())
                                      .amount(amount)
                                      .discountAmount(fundTransfer.getDiscountAmount())
                                      .discountRate(discountRatePercent)
                                      .actualPaidAmount(actualPaid)
                                      .transferStatus(fundTransfer.getStatus().name())
                                      .transferType(fundTransfer.getTransferType().name())
                                      .accountNumber(fundTransfer.getAccount().getAccountNumber())
                                      .bankName(fundTransfer.getAccount().getInstitution().getInstitutionName())
                                      .walletAddress(fundTransfer.getWallet().getAddress())
                                      .createdAt(fundTransfer.getCreatedAt())
                                      .updatedAt(fundTransfer.getUpdatedAt())
                                      .txHash(blockchainTx != null ? blockchainTx.getTxHash() : null)
                                      .blockchainStatus(blockchainTx != null ? blockchainTx.getStatus().name() : null)
                                      .build();
    }
}
