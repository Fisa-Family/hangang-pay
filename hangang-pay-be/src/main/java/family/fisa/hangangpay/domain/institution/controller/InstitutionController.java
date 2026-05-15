package family.fisa.hangangpay.domain.institution.controller;

import family.fisa.hangangpay.domain.institution.dto.DeployAllContractsResponse;
import family.fisa.hangangpay.domain.institution.service.InstitutionDeployService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Institution", description = "기관 및 컨트랙트 관리 API")
@RestController
@RequestMapping("/api/v1/institutions")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionDeployService institutionDeployService;

    @Operation(
            summary = "전체 컨트랙트 일괄 배포",
            description = "BoK CBDC, 은행별 예금토큰, BoK 정산 컨트랙트를 순서대로 배포한다.")
    @PostMapping("/contracts/deploy")
    public ResponseEntity<ApiResponse<DeployAllContractsResponse>> deployAllContracts() {
        DeployAllContractsResponse response = institutionDeployService.deployAll();

        return ResponseEntity.status(GeneralSuccessCode.COMMON_CREATED.getStatus())
                .body(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_CREATED, response));
    }
}
