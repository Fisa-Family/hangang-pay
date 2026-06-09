package family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainOutboxJpaRepository extends JpaRepository<BlockchainOutbox, Long> {

    List<BlockchainOutbox> findAllByStatus(BlockchainOutboxStatus status);

    Optional<BlockchainOutbox> findByBlockchainLedgerId(Long blockchainLedgerId);

    List<BlockchainOutbox> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            BlockchainOutboxStatus status, LocalDateTime updatedBefore, Pageable pageable);
}
