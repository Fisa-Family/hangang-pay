package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByParty_Id(Long partyId);

    Optional<Wallet> findByIdAndParty_Id(Long id, Long partyId);

    Optional<Wallet> findById(Long id);

    Wallet save(Wallet wallet);

    /** 비관적 쓰기 락 조회 - 동일 partyId 거래 동시성 제어용 */
    Optional<Wallet> findByParty_IdForUpdate(Long partyId);
}
