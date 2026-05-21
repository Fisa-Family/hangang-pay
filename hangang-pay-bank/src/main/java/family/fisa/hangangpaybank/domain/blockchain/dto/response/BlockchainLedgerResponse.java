package family.fisa.hangangpaybank.domain.blockchain.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import java.time.LocalDateTime;

public record BlockchainLedgerResponse(
        Long id,
        Long institutionId,
        String txHash,
        String status,
        Long blockNumber,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt) {

    public static BlockchainLedgerResponse from(BlockchainLedger ledger) {
        // 1. BlockchainLedger Entity의 핵심 필드만 노출
        return new BlockchainLedgerResponse(
                ledger.getId(),
                ledger.getInstitution().getId(),
                ledger.getTxHash(),
                ledger.getStatus() != null ? ledger.getStatus().name() : null,
                ledger.getBlockNumber(),
                ledger.getConfirmedAt(),
                ledger.getCreatedAt());
    }
}
