package family.fisa.hangangpaybank.domain.blockchain.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import java.util.Optional;

public interface BlockchainLedgerRepository {

    BlockchainLedger save(BlockchainLedger ledger);

    Optional<BlockchainLedger> findById(Long id);

    Optional<BlockchainLedger> findByTxHash(String txHash);
}
