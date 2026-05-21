package family.fisa.hangangpay.domain.institution.repository.jpa;

import family.fisa.hangangpay.domain.institution.entity.Institution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionJpaRepository extends JpaRepository<Institution, Long> {
    Optional<Institution> findByInstitutionCode(String institutionCode);

    List<Institution> findAllByOrderByIdAsc();
}
