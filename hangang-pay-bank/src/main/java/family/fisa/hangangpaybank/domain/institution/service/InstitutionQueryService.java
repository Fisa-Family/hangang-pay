package family.fisa.hangangpaybank.domain.institution.service;

import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.dto.response.InstitutionResponse;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstitutionQueryService {

    private final InstitutionRepository institutionRepository;

    public List<InstitutionResponse> getInstitutions() {
        // 1. 모든 기관 조회
        List<Institution> institutions = institutionRepository.findAll();

        // 2. Response DTO로 변환
        return institutions.stream().map(InstitutionResponse::from).toList();
    }

    public InstitutionResponse getInstitution(Long id) {
        // 1. ID로 기관 조회
        Institution institution =
                institutionRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        // 2. Response DTO로 변환
        return InstitutionResponse.from(institution);
    }
}
