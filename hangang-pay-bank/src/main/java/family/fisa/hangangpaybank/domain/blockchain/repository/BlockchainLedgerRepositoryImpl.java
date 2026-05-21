package family.fisa.hangangpaybank.domain.blockchain.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.repository.jpa.BlockchainLedgerJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
}
