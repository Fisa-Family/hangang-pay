package family.fisa.hangangpaybank.domain.blockchain.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import java.util.Optional;

public interface BlockchainLedgerRepository {

    BlockchainLedger save(BlockchainLedger ledger);

    Optional<BlockchainLedger> findById(Long id);

    Optional<BlockchainLedger> findByTxHash(String txHash);

    Optional<BlockchainLedger> findByIdempotentKey(String idempotentKey);
}
