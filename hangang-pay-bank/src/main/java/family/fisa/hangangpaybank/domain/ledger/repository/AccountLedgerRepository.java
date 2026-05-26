package family.fisa.hangangpaybank.domain.ledger.repository;

import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import java.util.List;
import java.util.Optional;

public interface AccountLedgerRepository {
    AccountLedger save(AccountLedger ledger);

    Optional<AccountLedger> findById(Long id);

    List<AccountLedger> findByBankAccountId(Long bankAccountId);

    Optional<AccountLedger> findByIdempotentKey(String idempotentKey);
}
