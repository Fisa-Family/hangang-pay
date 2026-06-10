package family.fisa.hangangpaybank.domain.blockchain.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.jpa.BlockchainLedgerJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BlockchainLedgerRepositoryImpl implements BlockchainLedgerRepository {

    private final BlockchainLedgerJpaRepository jpaRepository;

    @Override
    public BlockchainLedger save(BlockchainLedger ledger) {
        return jpaRepository.save(ledger);
    }

    @Override
    public Optional<BlockchainLedger> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<BlockchainLedger> findByTxHash(String txHash) {
        return jpaRepository.findByTxHash(txHash);
    }

    @Override
    public Optional<BlockchainLedger> findByIdempotentKey(String idempotentKey) {
        return jpaRepository.findByIdempotentKey(idempotentKey);
    }

    @Override
    public List<BlockchainLedger> findStaleByStatus(
            BlockchainTxStatus status, LocalDateTime updatedBefore, int limit) {
        return jpaRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                status, updatedBefore, PageRequest.of(0, limit));
    }

    @Override
    public List<BlockchainLedger> findStaleWithTxHashByStatus(
            BlockchainTxStatus status, LocalDateTime updatedBefore, int limit) {
        return jpaRepository.findByStatusAndTxHashIsNotNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                status, updatedBefore, PageRequest.of(0, limit));
    }
}
