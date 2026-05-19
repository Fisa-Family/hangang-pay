package family.fisa.hangangpay.domain.user.repository;

import family.fisa.hangangpay.domain.user.entity.User;
import java.util.Optional;

public interface UserRepository {

    Optional<Long> findPartyIdByUserId(Long userId);

    Optional<User> findByIdWithParty(Long userId);

    Optional<User> findByPhoneNumberWithParty(String phoneNumber);
}
