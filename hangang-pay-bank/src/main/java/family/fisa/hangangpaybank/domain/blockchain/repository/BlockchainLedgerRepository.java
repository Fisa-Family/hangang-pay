package family.fisa.hangangpaybank.domain.blockchain.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BlockchainLedgerRepository {

    BlockchainLedger save(BlockchainLedger ledger);

    Optional<BlockchainLedger> findById(Long id);

    Optional<BlockchainLedger> findByTxHash(String txHash);

    Optional<BlockchainLedger> findByIdempotentKey(String idempotentKey);

    List<BlockchainLedger> findStaleByStatus(
            BlockchainTxStatus status, LocalDateTime updatedBefore, int limit);

    List<BlockchainLedger> findStaleWithTxHashByStatus(
            BlockchainTxStatus status, LocalDateTime updatedBefore, int limit);
}
