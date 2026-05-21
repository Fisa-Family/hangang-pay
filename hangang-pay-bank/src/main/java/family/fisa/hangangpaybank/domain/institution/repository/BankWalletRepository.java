package family.fisa.hangangpaybank.domain.institution.repository;

import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankWalletRepository extends JpaRepository<BankWallet, Long> {

    Optional<BankWallet> findByWalletAddress(String walletAddress);
}
