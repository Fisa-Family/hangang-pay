package family.fisa.hangangpay.domain.institution.repository;

import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.jpa.InstitutionJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstitutionRepositoryImpl implements InstitutionRepository {

    private final InstitutionJpaRepository jpaRepository;

    @Override
    public Optional<Institution> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String code) {
        return jpaRepository.findByInstitutionCode(code);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
