package family.fisa.hangangpaybank.domain.institution.dto.response;

import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컨트랙트 배포 응답")
public record DeployContractResponse(
        @Schema(description = "기관 ID", example = "1") Long institutionId,
        @Schema(description = "기관명", example = "한국은행") String institutionName,
        @Schema(description = "배포 컨트랙트 종류", example = "CBDC") ContractType name,
        @Schema(description = "배포된 컨트랙트 주소", example = "0x1234567890abcdef1234567890abcdef12345678")
                String address,
        @Schema(
                        description = "배포 트랜잭션 해시",
                        example =
                                "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd")
                String transactionHash,
        @Schema(description = "배포에 사용한 RPC 엔드포인트", example = "http://localhost:8545")
                String rpcEndpoint,
        @Schema(
                        description = "배포 트랜잭션 서명자 주소",
                        example = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")
                String signerAddress) {}
