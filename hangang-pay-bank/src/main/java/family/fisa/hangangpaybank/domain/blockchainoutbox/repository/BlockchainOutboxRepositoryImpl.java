package family.fisa.hangangpaybank.domain.blockchainoutbox.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa.BlockchainOutboxJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BlockchainOutboxRepositoryImpl implements BlockchainOutboxRepository {

    private final BlockchainOutboxJpaRepository jpaRepository;

    @Override
    public BlockchainOutbox save(BlockchainOutbox outbox) {
        return jpaRepository.save(outbox);
    }

    @Override
    public List<BlockchainOutbox> findAllByStatus(BlockchainOutboxStatus status) {
        return jpaRepository.findAllByStatus(status);
    }

    @Override
    public List<BlockchainOutbox> findPublishableNew(int limit) {
        return jpaRepository.findPublishableNew(
                BlockchainOutboxStatus.NEW, BlockchainTxStatus.SUCCESS, PageRequest.of(0, limit));
    }

    @Override
    public Optional<BlockchainOutbox> findByBlockchainLedgerId(Long blockchainLedgerId) {
        return jpaRepository.findByBlockchainLedgerId(blockchainLedgerId);
    }

    @Override
    public List<BlockchainOutbox> findStaleByStatus(
            BlockchainOutboxStatus status, LocalDateTime updatedBefore, int limit) {
        return jpaRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                status, updatedBefore, PageRequest.of(0, limit));
    }
}
