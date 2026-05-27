package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import java.time.LocalDateTime;

public record PaymentStatusResponse(
        String transactionUuid,
        Long bankTransactionId,
        String status,
        String txHash,
        LocalDateTime confirmedAt) {

    /** blockchain_ledger 상태를 클라이언트 응답 상태로 변환한다 */
    public static PaymentStatusResponse of(String transactionUuid, BlockchainLedger ledger) {
        String mappedStatus = mapStatus(ledger.getStatus());
        return new PaymentStatusResponse(
                transactionUuid,
                ledger.getId(),
                mappedStatus,
                ledger.getTxHash(),
                ledger.getConfirmedAt());
    }

    private static String mapStatus(BlockchainTxStatus status) {
        return switch (status) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> "FAILED";
            case PENDING -> "PROCESSING";
        };
    }
}
