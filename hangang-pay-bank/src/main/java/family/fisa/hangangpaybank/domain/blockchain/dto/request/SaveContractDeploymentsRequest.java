package family.fisa.hangangpaybank.domain.blockchain.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "컨트랙트 배포 결과 저장 요청")
public record SaveContractDeploymentsRequest(
        @Schema(description = "배포 네트워크", example = "besu") String network,
        @Schema(description = "체인 ID", example = "1337") Long chainId,
        @Schema(description = "배포 완료 시각", example = "2026-05-25T12:00:00.000Z") String deployedAt,
        @ArraySchema(schema = @Schema(implementation = ContractDeploymentRequest.class))
                List<ContractDeploymentRequest> contracts) {}
