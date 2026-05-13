package family.fisa.hangangpay.domain.blockchain.repository;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainTxRepository extends JpaRepository<BlockchainTx, Long> {}
