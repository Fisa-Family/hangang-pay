package family.fisa.hangangpaybank.domain.blockchainoutbox.repository;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa.BlockchainOutboxJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
}
