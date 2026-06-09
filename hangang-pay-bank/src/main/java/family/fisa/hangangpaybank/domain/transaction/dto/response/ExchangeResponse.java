package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

/** 환전 응답 */
@Builder
public record ExchangeResponse(
        String transactionUuid,
        Long bankTransactionId,
        String status,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal accountBalance) {

    /** 비동기 접수. 토큰 burn 진행 전이므로 온체인 정보는 null, status=PROCESSING */
    public static ExchangeResponse accepted(
            ExchangeRequest request, AccountLedger accountLedger, BigDecimal accountBalance) {
        return ExchangeResponse.builder()
                .transactionUuid(request.transactionUuid())
                .bankTransactionId(accountLedger.getId())
                .status("PROCESSING")
                .txHash(null)
                .blockNumber(null)
                .confirmedAt(null)
                .accountBalance(accountBalance)
                .build();
    }

    /** 멱등 재요청 등에서 이미 완료된 환전을 blockchain_ledger 기준으로 재구성 */
    public static ExchangeResponse from(
            String transactionUuid,
            AccountLedger accountLedger,
            BlockchainLedger blockchainLedger,
            BigDecimal accountBalance) {
        return ExchangeResponse.builder()
                .transactionUuid(transactionUuid)
                .bankTransactionId(accountLedger.getId())
                .status(blockchainLedger.getStatus().name())
                .txHash(blockchainLedger.getTxHash())
                .blockNumber(blockchainLedger.getBlockNumber())
                .confirmedAt(blockchainLedger.getConfirmedAt())
                .accountBalance(accountBalance)
                .build();
    }
}
