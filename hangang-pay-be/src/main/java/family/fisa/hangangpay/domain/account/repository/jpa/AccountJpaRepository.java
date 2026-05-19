package family.fisa.hangangpay.domain.account.repository.jpa;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = {"institution"})
    List<Account> findAllByParty_Id(Long partyId);

    long countByParty_Id(Long partyId);

    boolean existsByParty_IdAndAccountNumber(Long partyId, String accountNumber);

    Optional<Account> findByIdAndParty_Id(Long id, Long partyId);

    Optional<Account> findByParty_IdAndAccountType(Long partyId, AccountType accountType);
}
