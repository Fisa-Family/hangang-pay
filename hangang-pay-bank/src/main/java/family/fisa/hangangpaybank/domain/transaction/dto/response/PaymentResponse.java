package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String transactionUuid,
        Long bankTransactionId,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {

    /** blockchain_ledger와 지갑 잔액으로 응답을 생성한다 */
    public static PaymentResponse from(
            BlockchainLedger ledger,
            String transactionUuid,
            BigDecimal fromBalance,
            BigDecimal toBalance) {
        return new PaymentResponse(
                transactionUuid,
                ledger.getId(),
                ledger.getTxHash(),
                ledger.getBlockNumber(),
                ledger.getConfirmedAt(),
                fromBalance,
                toBalance);
    }
}
