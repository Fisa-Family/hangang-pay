package family.fisa.hangangpaybank.domain.blockchain.controller;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainSuccessCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.request.SaveContractDeploymentsRequest;
import family.fisa.hangangpaybank.domain.blockchain.dto.response.SaveContractDeploymentsResponse;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractDeploymentCommandService;
import family.fisa.hangangpaybank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal Blockchain", description = "내부 블록체인 배포 연동 API")
@RestController
@RequestMapping("/api/v1/internal/blockchain/deployments")
@RequiredArgsConstructor
public class InternalBlockchainDeploymentController {

    private final ContractDeploymentCommandService contractDeploymentCommandService;

    @Operation(summary = "컨트랙트 배포 결과 저장", description = "Hardhat UUPS 배포 결과의 proxy 주소를 DB에 저장한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<SaveContractDeploymentsResponse>> saveDeployments(
            @RequestBody SaveContractDeploymentsRequest request) {
        SaveContractDeploymentsResponse response = contractDeploymentCommandService.save(request);

        return ResponseEntity.status(
                        BlockchainSuccessCode.BLOCKCHAIN_CONTRACTS_DEPLOYED.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                BlockchainSuccessCode.BLOCKCHAIN_CONTRACTS_DEPLOYED, response));
    }
}
