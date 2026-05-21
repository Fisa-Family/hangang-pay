package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByParty_Id(Long partyId);

    Wallet save(Wallet wallet);
}
