package family.fisa.hangangpay.domain.transfer.dto.response;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record UserExchangeHistoryDetail(
        Long historyId,
        BigDecimal amount,
        String transferStatus,
        String transferType,
        String accountNumber,
        String bankName,
        String walletAddress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String txHash,
        String blockchainStatus) {
    public static UserExchangeHistoryDetail from(
            FundTransfer fundTransfer, BlockchainTx blockchainTx) {
        return UserExchangeHistoryDetail.builder()
                .historyId(fundTransfer.getId())
                .amount(fundTransfer.getAmount())
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
