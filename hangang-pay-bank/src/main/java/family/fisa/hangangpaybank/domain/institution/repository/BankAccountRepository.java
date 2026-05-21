package family.fisa.hangangpaybank.domain.institution.repository;

import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByInstitution_IdAndAccountNumber(
            Long institutionId, String accountNumber);
}
