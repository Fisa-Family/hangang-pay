package family.fisa.hangangpay.domain.merchant.repository;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.jpa.MerchantJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MerchantRepositoryImpl implements MerchantRepository {

    private final MerchantJpaRepository merchantJpaRepository;

    @Override
    public List<Merchant> findByParty_IdIn(List<Long> payeePartyIds) {
        return merchantJpaRepository.findByParty_IdIn(payeePartyIds);
    }

    @Override
    public Optional<Merchant> findByParty_Id(Long partyId) {
        return merchantJpaRepository.findByParty_Id(partyId);
    }

    @Override
    public Optional<Merchant> findByPhoneNumberWithParty(String phoneNumber) {
        return merchantJpaRepository.findByPhoneNumberWithParty(phoneNumber);
    }
}
