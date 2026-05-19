package family.fisa.hangangpay.domain.merchant.repository;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import java.util.List;
import java.util.Optional;

public interface MerchantRepository {
    List<Merchant> findByPartyIdIn(List<Long> payeePartyIds);

    Optional<Merchant> findByPhoneNumberWithParty(String phoneNumber);
}
