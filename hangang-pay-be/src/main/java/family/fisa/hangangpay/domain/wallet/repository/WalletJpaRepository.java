package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByParty_Id(Long partyId);

    Optional<Wallet> findByIdAndParty_Id(Long id, Long partyId);

    Wallet save(Wallet wallet);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.party.id = :partyId")
    Optional<Wallet> findByParty_IdForUpdate(@Param("partyId") Long partyId);
}
