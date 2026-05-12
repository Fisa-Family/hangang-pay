package family.fisa.hangangpay.domain.user.repository;

import family.fisa.hangangpay.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
