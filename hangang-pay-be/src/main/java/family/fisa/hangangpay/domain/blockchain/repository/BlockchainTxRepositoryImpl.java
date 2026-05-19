package family.fisa.hangangpay.domain.blockchain.repository;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BlockchainTxRepositoryImpl implements BlockchainTxRepository {
    private final BlockchainTxJpaRepository blockchainTxJpaRepository;

    @Override
    public Optional<BlockchainTx> findByReferenceTypeAndReferenceId(
            ReferenceType referenceType, Long referenceId) {
        return blockchainTxJpaRepository.findByReferenceTypeAndReferenceId(
                referenceType, referenceId);
    }
}
