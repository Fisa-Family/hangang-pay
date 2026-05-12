package family.fisa.hangangpay.domain.account.repository;

import family.fisa.hangangpay.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
