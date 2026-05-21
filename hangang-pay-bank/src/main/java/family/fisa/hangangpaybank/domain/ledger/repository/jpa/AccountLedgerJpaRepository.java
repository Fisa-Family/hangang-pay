package family.fisa.hangangpaybank.domain.ledger.repository.jpa;

import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerJpaRepository extends JpaRepository<AccountLedger, Long> {

    List<AccountLedger> findByBankAccount_Id(Long bankAccountId);
}
