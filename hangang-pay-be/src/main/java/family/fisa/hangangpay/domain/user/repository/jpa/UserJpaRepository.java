package family.fisa.hangangpay.domain.user.repository.jpa;

import family.fisa.hangangpay.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    @Query("select u.party.id from User u where u.id = :userId")
    Optional<Long> findPartyIdByUserId(@Param("userId") Long userId);

    @Query("select u from User u join fetch u.party where u.id = :userId")
    Optional<User> findByIdWithParty(@Param("userId") Long userId);

    @Query("select u from User u join fetch u.party where u.phoneNumber = :phoneNumber")
    Optional<User> findByPhoneNumberWithParty(@Param("phoneNumber") String phoneNumber);

    Optional<User> findByParty_Id(Long partyId);
}
