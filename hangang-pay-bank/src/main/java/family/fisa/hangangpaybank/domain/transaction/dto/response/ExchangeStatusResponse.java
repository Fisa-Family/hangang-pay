package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import lombok.Builder;

@Builder
public record ExchangeStatusResponse(
        String transactionUuid, Long bankTransactionId, String txHash) {

    public static ExchangeStatusResponse of(
            String transactionUuid,
            AccountLedger accountLedger,
            BlockchainLedger blockchainLedger) {
        return ExchangeStatusResponse.builder()
                .transactionUuid(transactionUuid)
                .bankTransactionId(accountLedger.getId())
                .txHash(blockchainLedger.getTxHash())
                .build();
    }
}
