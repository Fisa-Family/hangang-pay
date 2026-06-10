package family.fisa.hangangpaybank.domain.ledger.repository;

import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedger;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {

    Optional<WalletLedger> findByTransactionUuidAndBankWallet(
            String transactionUuid, BankWallet bankWallet);

    Optional<WalletLedger> findFirstByTransactionUuid(String transactionUuid);
}
