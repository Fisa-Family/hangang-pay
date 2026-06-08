package family.fisa.hangangpaybank.domain.blockchainoutbox.repository;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import java.util.List;

public interface BlockchainOutboxRepository {

    BlockchainOutbox save(BlockchainOutbox outbox);

    List<BlockchainOutbox> findAllByStatus(BlockchainOutboxStatus status);
}
