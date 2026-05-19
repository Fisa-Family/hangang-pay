package family.fisa.hangangpay.domain.blockchain.repository;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainTxJpaRepository extends JpaRepository<BlockchainTx, Long> {

    Optional<BlockchainTx> findByReferenceTypeAndReferenceId(
            ReferenceType referenceType, Long referenceId);
}
