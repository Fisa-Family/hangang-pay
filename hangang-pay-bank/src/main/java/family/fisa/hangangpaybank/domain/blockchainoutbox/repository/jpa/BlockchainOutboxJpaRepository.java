package family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainOutboxJpaRepository extends JpaRepository<BlockchainOutbox, Long> {

    List<BlockchainOutbox> findAllByStatus(BlockchainOutboxStatus status);
}
