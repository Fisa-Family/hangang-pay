package family.fisa.hangangpay.domain.blockchain.repository;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import java.util.Optional;

public interface BlockchainTxRepository {

    /** 블록체인 트랜잭션 저장 */
    BlockchainTx save(BlockchainTx blockchainTx);

    Optional<BlockchainTx> findByReferenceTypeAndReferenceId(
            ReferenceType referenceType, Long referenceId);
}
