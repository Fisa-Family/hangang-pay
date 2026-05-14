package family.fisa.hangangpay.domain.institution.dto;

import family.fisa.hangangpay.domain.institution.entity.ContractType;

public record DeployContractResponse(
        Long institutionId,
        String institutionName,
        ContractType name,
        String address,
        String transactionHash,
        String rpcEndpoint,
        String signerAddress) {}
