package family.fisa.hangangpaybank.domain.blockchain.repository.jpa;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainLedgerJpaRepository extends JpaRepository<BlockchainLedger, Long> {

    Optional<BlockchainLedger> findByTxHash(String txHash);

    Optional<BlockchainLedger> findByIdempotentKey(String idempotentKey);

    List<BlockchainLedger> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            BlockchainTxStatus status, LocalDateTime updatedBefore, Pageable pageable);

    List<BlockchainLedger> findByStatusAndTxHashIsNotNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            BlockchainTxStatus status, LocalDateTime updatedBefore, Pageable pageable);
}
