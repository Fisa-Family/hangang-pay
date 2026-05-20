package family.fisa.hangangpay.domain.institution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.dto.ContractArtifact;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** resources/contracts/*.json에서 Hardhat artifact를 읽어 배포용 bytecode를 제공한다. */
@Component
@RequiredArgsConstructor
public class TokenArtifactLoader {

    private static final String CONTRACT_ARTIFACT_PATH = "contracts/";
    private static final String CBDC_ARTIFACT = "CBDCToken.json";
    private static final String DEPOSIT_TOKEN_ARTIFACT = "DepositToken.json";
    private static final String SETTLEMENT_ARTIFACT = "Settlement.json";
    private static final String LOCAL_CURRENCY_ARTIFACT = "LocalCurrencyPolicy.json";

    private final ObjectMapper objectMapper;

    public ContractArtifact localCurrencyArtifact() {

        return load(LOCAL_CURRENCY_ARTIFACT);
    }

    public ContractArtifact cbdcArtifact() {
        return load(CBDC_ARTIFACT);
    }

    public ContractArtifact depositTokenArtifact() {
        return load(DEPOSIT_TOKEN_ARTIFACT);
    }

    public ContractArtifact settlementArtifact() {
        return load(SETTLEMENT_ARTIFACT);
    }

    private ContractArtifact load(String filename) {
        ClassPathResource resource = new ClassPathResource(CONTRACT_ARTIFACT_PATH + filename);

        if (!resource.exists()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_ARTIFACT_NOT_FOUND);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode artifact = objectMapper.readTree(inputStream);
            String bytecode = artifact.path("bytecode").asText();

            if (bytecode == null || bytecode.isBlank()) {
                throw new BusinessException(InstitutionErrorCode.INSTITUTION_ARTIFACT_NOT_FOUND);
            }

            return new ContractArtifact(bytecode);
        } catch (IOException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_ARTIFACT_NOT_FOUND);
        }
    }
}
