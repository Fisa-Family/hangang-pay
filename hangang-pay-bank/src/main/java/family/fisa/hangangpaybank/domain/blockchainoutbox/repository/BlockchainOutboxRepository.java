package family.fisa.hangangpaybank.domain.blockchainoutbox.repository;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BlockchainOutboxRepository {

    BlockchainOutbox save(BlockchainOutbox outbox);

    List<BlockchainOutbox> findAllByStatus(BlockchainOutboxStatus status);

    Optional<BlockchainOutbox> findByBlockchainLedgerId(Long blockchainLedgerId);

    List<BlockchainOutbox> findStaleByStatus(
            BlockchainOutboxStatus status, LocalDateTime updatedBefore, int limit);
}
