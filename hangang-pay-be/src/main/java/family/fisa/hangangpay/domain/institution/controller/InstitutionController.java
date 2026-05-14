package family.fisa.hangangpay.domain.institution.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import family.fisa.hangangpay.domain.institution.dto.DeployContractRequest;
import family.fisa.hangangpay.domain.institution.dto.DeployContractResponse;
import family.fisa.hangangpay.domain.institution.service.InstitutionDeployService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Institution", description = "기관 및 컨트랙트 관리 API")
@RestController
@RequestMapping("/api/v1/institutions")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionDeployService institutionDeployService;

    @Operation(summary = "기관 컨트랙트 배포", description = "기관 지갑으로 CBDC, 예금토큰, 정산 컨트랙트를 배포한다. 정산 컨트랙트는 가장 마지막에 배포한다.")
    @PostMapping("/{institutionId}/contracts")
    public ResponseEntity<ApiResponse<DeployContractResponse>> deployContract(
            @PathVariable Long institutionId,
            @RequestBody(required = false) DeployContractRequest request) {
        DeployContractResponse response = institutionDeployService.deploy(institutionId, request);

        return ResponseEntity.status(GeneralSuccessCode.CREATED.getStatus())
                .body(ApiResponse.onSuccess(GeneralSuccessCode.CREATED, response));
    }
}
