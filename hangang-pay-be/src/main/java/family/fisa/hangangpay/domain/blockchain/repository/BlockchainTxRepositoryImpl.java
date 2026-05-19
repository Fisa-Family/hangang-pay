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

    /** 블록체인 트랜잭션 저장 위임 */
    @Override
    public BlockchainTx save(BlockchainTx blockchainTx) {
        return blockchainTxJpaRepository.save(blockchainTx);
    }

    @Override
    public Optional<BlockchainTx> findByReferenceTypeAndReferenceId(
            ReferenceType referenceType, Long referenceId) {
        return blockchainTxJpaRepository.findByReferenceTypeAndReferenceId(
                referenceType, referenceId);
    }
}
