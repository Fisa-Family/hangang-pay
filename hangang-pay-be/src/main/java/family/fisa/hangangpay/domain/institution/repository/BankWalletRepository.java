package family.fisa.hangangpay.domain.institution.repository;

import family.fisa.hangangpay.domain.institution.entity.BankWallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankWalletRepository extends JpaRepository<BankWallet, Long> {
}
