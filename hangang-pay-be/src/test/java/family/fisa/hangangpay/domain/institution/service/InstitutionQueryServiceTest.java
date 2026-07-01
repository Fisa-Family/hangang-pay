package family.fisa.hangangpay.domain.institution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpay.domain.institution.code.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstitutionQueryServiceTest {

    private static final Long INSTITUTION_ID = 1L;
    private static final String INSTITUTION_CODE = "WR";

    @Mock private InstitutionRepository institutionRepository;

    @InjectMocks private InstitutionQueryService institutionQueryService;

    @Test
    @DisplayName("기관 식별자로 기관을 조회한다")
    void getById() {
        Institution institution = institution();
        given(institutionRepository.findById(INSTITUTION_ID)).willReturn(Optional.of(institution));

        Institution result = institutionQueryService.getById(INSTITUTION_ID);

        assertThat(result).isEqualTo(institution);
    }

    @Test
    @DisplayName("기관 식별자로 조회한 기관이 없으면 예외를 던진다")
    void getByIdWithMissingInstitution() {
        given(institutionRepository.findById(INSTITUTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> institutionQueryService.getById(INSTITUTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_NOT_FOUND);
    }

    @Test
    @DisplayName("기관 코드로 기관을 조회한다")
    void getByCode() {
        Institution institution = institution();
        given(institutionRepository.findByInstitutionCode(INSTITUTION_CODE))
                .willReturn(Optional.of(institution));

        Institution result = institutionQueryService.getByCode(INSTITUTION_CODE);

        assertThat(result).isEqualTo(institution);
    }

    @Test
    @DisplayName("기관 목록을 식별자 오름차순으로 조회한다")
    void getInstitutions() {
        List<Institution> institutions = List.of(institution());
        given(institutionRepository.findAllByOrderByIdAsc()).willReturn(institutions);

        List<Institution> result = institutionQueryService.getInstitutions();

        assertThat(result).isEqualTo(institutions);
    }

    private Institution institution() {
        return Institution.builder()
                .id(INSTITUTION_ID)
                .institutionCode(INSTITUTION_CODE)
                .institutionName("우리은행")
                .build();
    }
}
