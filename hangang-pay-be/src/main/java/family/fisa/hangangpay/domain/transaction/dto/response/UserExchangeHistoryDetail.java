package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
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
            Transaction transaction, BlockchainLedgerResponse blockchainLedger) {
        return UserExchangeHistoryDetail.builder()
                .historyId(transaction.getId())
                .amount(transaction.getAmount())
                .transferStatus(transaction.getStatus().name())
                .transferType(transaction.getTransactionType().name())
                .accountNumber(transaction.getToAccount().getAccountNumber())
                .bankName(transaction.getToAccount().getInstitution().getInstitutionName())
                .walletAddress(transaction.getFromWallet().getAddress())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .txHash(transaction.getTxHash())
                .blockchainStatus(blockchainLedger != null ? blockchainLedger.status() : null)
                .build();
    }
}
