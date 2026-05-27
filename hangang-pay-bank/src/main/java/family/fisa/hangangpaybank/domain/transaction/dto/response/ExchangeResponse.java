package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ExchangeResponse(
        String transactionUuid,
        Long bankTransactionId,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal accountBalance) {

    public static ExchangeResponse of(
            ExchangeRequest request,
            AccountLedger accountLedger,
            BlockchainLedger blockchainLedger,
            BigDecimal balance) {
        return ExchangeResponse.builder()
                .transactionUuid(request.transactionUuid())
                .bankTransactionId(accountLedger.getId())
                .txHash(blockchainLedger.getTxHash())
                .blockNumber(blockchainLedger.getBlockNumber())
                .confirmedAt(blockchainLedger.getConfirmedAt())
                .accountBalance(balance)
                .build();
    }
}
