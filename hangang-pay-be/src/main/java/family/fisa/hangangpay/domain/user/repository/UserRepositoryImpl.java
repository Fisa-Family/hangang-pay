package family.fisa.hangangpay.domain.user.repository;

import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.jpa.UserJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<Long> findPartyIdByUserId(@Param("userId") Long userId) {
        return userJpaRepository.findPartyIdByUserId(userId);
    }

    @Override
    public Optional<User> findByIdWithParty(Long userId) {
        return userJpaRepository.findByIdWithParty(userId);
    }

    @Override
    public Optional<User> findByPhoneNumberWithParty(String phoneNumber) {
        return userJpaRepository.findByPhoneNumberWithParty(phoneNumber);
    }
}
