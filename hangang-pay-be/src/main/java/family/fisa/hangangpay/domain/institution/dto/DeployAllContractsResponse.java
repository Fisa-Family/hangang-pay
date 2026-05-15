package family.fisa.hangangpay.domain.institution.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "전체 컨트랙트 배포 응답")
public record DeployAllContractsResponse(
        @ArraySchema(schema = @Schema(implementation = DeployContractResponse.class))
                @Schema(description = "배포된 컨트랙트 목록")
                List<DeployContractResponse> contracts) {}
