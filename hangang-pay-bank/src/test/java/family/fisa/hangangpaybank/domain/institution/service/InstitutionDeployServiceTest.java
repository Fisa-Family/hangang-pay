package family.fisa.hangangpaybank.domain.institution.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.Contract;
import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.entity.InstitutionCode;
import family.fisa.hangangpaybank.domain.institution.repository.ContractRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;

@ExtendWith(MockitoExtension.class)
class InstitutionDeployServiceTest {

    private static final String PRIVATE_KEY =
            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";

    private static final String WALLET_ADDRESS = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";

    @Mock private WalletKeyCipher walletKeyCipher;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private ContractRepository contractAddressRepository;

    @InjectMocks private InstitutionDeployService institutionDeployService;

    @Test
    @DisplayName("BoK 기관을 찾을 수 없으면 INSTITUTION_NOT_FOUND 예외를 던진다")
    void deployAllCentralBankNotFound() {
        given(institutionRepository.findByInstitutionCode(InstitutionCode.BOK.getCode()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> institutionDeployService.deployAll())
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_NOT_FOUND);

        verify(contractAddressRepository, never()).findByInstitutionIdAndName(any(), any());
    }

    @Test
    @DisplayName("우리은행 기관을 찾을 수 없으면 INSTITUTION_NOT_FOUND 예외를 던진다")
    void deployAllWooriBankNotFound() {
        Institution centralBank =
                institution(1L, InstitutionCode.BOK.getCode(), "한국은행", WALLET_ADDRESS);

        given(institutionRepository.findByInstitutionCode(InstitutionCode.BOK.getCode()))
                .willReturn(Optional.of(centralBank));
        given(institutionRepository.findByInstitutionCode(InstitutionCode.WOORI.getCode()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> institutionDeployService.deployAll())
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_NOT_FOUND);

        verify(contractAddressRepository, never()).findByInstitutionIdAndName(any(), any());
    }

    @Test
    @DisplayName("BoK 기관 배포 정보가 부족하면 INSTITUTION_MISSING_DEPLOYMENT_INFO 예외를 던진다")
    void deployAllMissingDeploymentInfo() {
        Institution centralBank =
                Institution.builder()
                        .id(1L)
                        .institutionCode(InstitutionCode.BOK.getCode())
                        .institutionName("한국은행")
                        .build();

        Institution wooriBank =
                institution(2L, InstitutionCode.WOORI.getCode(), "우리은행", WALLET_ADDRESS);

        given(institutionRepository.findByInstitutionCode(InstitutionCode.BOK.getCode()))
                .willReturn(Optional.of(centralBank));
        given(institutionRepository.findByInstitutionCode(InstitutionCode.WOORI.getCode()))
                .willReturn(Optional.of(wooriBank));

        assertThatThrownBy(() -> institutionDeployService.deployAll())
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_MISSING_DEPLOYMENT_INFO);

        verify(contractAddressRepository, never()).findByInstitutionIdAndName(any(), any());
    }

    @Test
    @DisplayName("BoK CBDC 컨트랙트가 이미 배포되어 있으면 INSTITUTION_CONTRACT_ALREADY_DEPLOYED 예외를 던진다")
    void deployAllCbdcAlreadyDeployed() {
        Institution centralBank =
                institution(1L, InstitutionCode.BOK.getCode(), "한국은행", WALLET_ADDRESS);

        Institution wooriBank =
                institution(2L, InstitutionCode.WOORI.getCode(), "우리은행", WALLET_ADDRESS);

        Contract existingContract =
                Contract.builder()
                        .institution(centralBank)
                        .name(ContractType.CBDC)
                        .address("0x0000000000000000000000000000000000000001")
                        .build();

        given(institutionRepository.findByInstitutionCode(InstitutionCode.BOK.getCode()))
                .willReturn(Optional.of(centralBank));
        given(institutionRepository.findByInstitutionCode(InstitutionCode.WOORI.getCode()))
                .willReturn(Optional.of(wooriBank));
        given(contractAddressRepository.findByInstitutionIdAndName(1L, ContractType.CBDC))
                .willReturn(Optional.of(existingContract));

        assertThatThrownBy(() -> institutionDeployService.deployAll())
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_CONTRACT_ALREADY_DEPLOYED);

        verify(contractAddressRepository).findByInstitutionIdAndName(1L, ContractType.CBDC);
    }

    @Test
    @DisplayName("BoK 지갑 주소와 개인키 주소가 다르면 INSTITUTION_INVALID_WALLET_KEY 예외를 던진다")
    void deployAllInvalidWalletKey() {
        Institution centralBank =
                institution(
                        1L,
                        InstitutionCode.BOK.getCode(),
                        "한국은행",
                        "0x0000000000000000000000000000000000000001");

        Institution wooriBank =
                institution(2L, InstitutionCode.WOORI.getCode(), "우리은행", WALLET_ADDRESS);

        given(institutionRepository.findByInstitutionCode(InstitutionCode.BOK.getCode()))
                .willReturn(Optional.of(centralBank));
        given(institutionRepository.findByInstitutionCode(InstitutionCode.WOORI.getCode()))
                .willReturn(Optional.of(wooriBank));
        given(contractAddressRepository.findByInstitutionIdAndName(1L, ContractType.CBDC))
                .willReturn(Optional.empty());
        given(walletKeyCipher.decryptCredentials(PRIVATE_KEY))
                .willReturn(Credentials.create(PRIVATE_KEY));

        assertThatThrownBy(() -> institutionDeployService.deployAll())
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_INVALID_WALLET_KEY);
    }

    private static Institution institution(
            Long id, String institutionCode, String institutionName, String walletAddress) {
        return Institution.builder()
                .id(id)
                .institutionCode(institutionCode)
                .institutionName(institutionName)
                .walletAddress(walletAddress)
                .encryptedPrivateKey(PRIVATE_KEY)
                .rpcEndpoint("http://localhost:8545")
                .build();
    }
}
