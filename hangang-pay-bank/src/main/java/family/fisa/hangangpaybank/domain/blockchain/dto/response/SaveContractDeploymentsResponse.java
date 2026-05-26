package family.fisa.hangangpaybank.domain.blockchain.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "컨트랙트 배포 결과 저장 응답")
public record SaveContractDeploymentsResponse(
        @ArraySchema(schema = @Schema(implementation = SavedContractDeploymentResponse.class))
                List<SavedContractDeploymentResponse> contracts) {}
