package family.fisa.hangangpaybank.domain.institution.repository;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    Optional<Institution> findByInstitutionCode(String institutionCode);

    List<Institution> findAllByOrderByIdAsc();
}
