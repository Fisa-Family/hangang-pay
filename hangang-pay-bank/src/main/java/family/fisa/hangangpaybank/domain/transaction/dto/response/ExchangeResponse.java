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

    /**
     * 동기 접수 응답: off-chain DB 처리(토큰 차감/현금 입금/account_ledger) 완료 = SUCCESS. 블록체인 burn은 비동기로 디커플되며,
     * 온체인 정보(txHash 등)는 아직 없어 null이고 상태조회로 확인한다. (결제 동기 응답과 동일 의미)
     */
    public static ExchangeResponse accepted(
            ExchangeRequest request, AccountLedger accountLedger, BigDecimal accountBalance) {
        return ExchangeResponse.builder()
                .transactionUuid(request.transactionUuid())
                .bankTransactionId(accountLedger.getId())
                .status("SUCCESS")
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
