package family.fisa.hangangpay.domain.institution.service;

import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstitutionQueryService {

    private final InstitutionRepository institutionRepository;

    public Institution getById(Long institutionId) {
        return institutionRepository
                .findById(institutionId)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));
    }

    public Institution getByCode(String institutionCode) {
        return institutionRepository
                .findByInstitutionCode(institutionCode)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));
    }

    public List<Institution> getInstitutions() {
        return institutionRepository.findAllByOrderByIdAsc();
    }
}
