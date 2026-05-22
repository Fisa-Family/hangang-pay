package family.fisa.hangangpaybank.domain.institution.controller;

import family.fisa.hangangpaybank.domain.institution.code.InstitutionSuccessCode;
import family.fisa.hangangpaybank.domain.institution.dto.response.DeployAllContractsResponse;
import family.fisa.hangangpaybank.domain.institution.service.InstitutionDeployService;
import family.fisa.hangangpaybank.global.response.ApiResponse;
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
            description = "BoK CBDC, 예금토큰, BoK 정산, 지역화폐 컨트랙트를 순서대로 배포한다.")
    @PostMapping("/contracts/deploy")
    public ResponseEntity<ApiResponse<DeployAllContractsResponse>> deployAllContracts() {
        DeployAllContractsResponse response = institutionDeployService.deployAll();

        return ResponseEntity.status(InstitutionSuccessCode.CONTRACTS_DEPLOYED.getStatus())
                .body(ApiResponse.onSuccess(InstitutionSuccessCode.CONTRACTS_DEPLOYED, response));
    }
}
