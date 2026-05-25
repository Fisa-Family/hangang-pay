package family.fisa.hangangpaybank.domain.blockchain.repository.jpa;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainLedgerJpaRepository extends JpaRepository<BlockchainLedger, Long> {

    Optional<BlockchainLedger> findByTxHash(String txHash);
    Optional<BlockchainLedger> findByIdempotentKey(String idempotentKey);
}
