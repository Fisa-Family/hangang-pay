package family.fisa.hangangpay.domain.blockchain.repository;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import java.util.Optional;

public interface BlockchainTxRepository {
    Optional<BlockchainTx> findByReferenceTypeAndReferenceId(
            ReferenceType referenceType, Long referenceId);
}
