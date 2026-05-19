package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /** 파티 식별자로 지갑 단건 조회 */
    Optional<Wallet> findByParty_Id(Long partyId);
}
