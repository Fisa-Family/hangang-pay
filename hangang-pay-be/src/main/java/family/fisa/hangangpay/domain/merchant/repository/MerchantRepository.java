package family.fisa.hangangpay.domain.merchant.repository;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    List<Merchant> findByParty_IdIn(List<Long> payeePartyIds);
}
