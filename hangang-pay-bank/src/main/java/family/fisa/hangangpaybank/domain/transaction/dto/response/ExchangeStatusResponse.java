package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import lombok.Builder;

@Builder
public record ExchangeStatusResponse(
        String transactionUuid, Long bankTransactionId, String status, String txHash) {

    public static ExchangeStatusResponse of(
            String transactionUuid,
            AccountLedger accountLedger,
            BlockchainLedger blockchainLedger) {
        return ExchangeStatusResponse.builder()
                .transactionUuid(transactionUuid)
                .bankTransactionId(accountLedger.getId())
                .status(mapStatus(blockchainLedger.getStatus()))
                .txHash(blockchainLedger.getTxHash())
                .build();
    }

    /** blockchain_ledger 상태를 응답 상태로 변환 (결제 PaymentStatusResponse와 동일 매핑) */
    private static String mapStatus(BlockchainTxStatus status) {
        return switch (status) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> "FAILED";
            case PENDING, SUBMITTED -> "PROCESSING";
        };
    }
}
