package family.fisa.hangangpay.domain.merchant.repository;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantJpaRepository extends JpaRepository<Merchant, Long> {
    List<Merchant> findByParty_IdIn(List<Long> payeePartyIds);

    Optional<Merchant> findByParty_Id(Long partyId);
}
