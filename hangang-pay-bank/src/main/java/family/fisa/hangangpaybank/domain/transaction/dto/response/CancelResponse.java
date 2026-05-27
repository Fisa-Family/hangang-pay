package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CancelResponse(
        String transactionUuid,
        String originalTransactionUuid,
        Long bankTransactionId,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {

    /** blockchain_ledger와 지갑 잔액으로 응답을 생성한다 */
    public static CancelResponse from(
            BlockchainLedger ledger,
            String transactionUuid,
            String originalTransactionUuid,
            BigDecimal fromBalance,
            BigDecimal toBalance) {
        return new CancelResponse(
                transactionUuid,
                originalTransactionUuid,
                ledger.getId(),
                ledger.getTxHash(),
                ledger.getBlockNumber(),
                ledger.getConfirmedAt(),
                fromBalance,
                toBalance);
    }
}
