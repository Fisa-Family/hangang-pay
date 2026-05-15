package family.fisa.hangangpay.domain.institution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import family.fisa.hangangpay.domain.institution.entity.Institution;

/** 금융기관 데이터 접근 레이어 */
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    /** 기관 코드로 금융기관 단건 조회 */
    Optional<Institution> findByInstitutionCode(String institutionCode);

    List<Institution> findAllByOrderByIdAsc();
}
