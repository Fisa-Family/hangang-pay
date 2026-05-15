package family.fisa.hangangpay.domain.institution.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.institution.dto.DeployContractRequest;
import family.fisa.hangangpay.domain.institution.entity.ContractAddress;
import family.fisa.hangangpay.domain.institution.entity.ContractType;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.exception.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.repository.ContractAddressRepository;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
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

    @Mock private TokenArtifactLoader tokenArtifactLoader;
    @Mock private WalletKeyCipher walletKeyCipher;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private ContractAddressRepository contractAddressRepository;

    @InjectMocks private InstitutionDeployService institutionDeployService;

    @Test
    @DisplayName("기관을 찾을 수 없으면 NOT_FOUND 예외를 던진다")
    void deployInstitutionNotFound() {
        given(institutionRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> institutionDeployService.deploy(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.NOT_FOUND);

        verify(contractAddressRepository, never()).findByInstitutionIdAndName(any(), any());
    }

    @Test
    @DisplayName("기관 배포 정보가 부족하면 MISSING_DEPLOYMENT_INFO 예외를 던진다")
    void deployMissingDeploymentInfo() {
        Institution institution =
                Institution.builder().id(1L).institutionCode("BoK").institutionName("한국은행").build();

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));

        assertThatThrownBy(() -> institutionDeployService.deploy(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.MISSING_DEPLOYMENT_INFO);

        verify(contractAddressRepository, never()).findByInstitutionIdAndName(any(), any());
    }

    @Test
    @DisplayName("요청 name이 없으면 BoK 기관은 CBDC 배포 대상으로 판단한다")
    void deployDefaultCbdcForBok() {
        Institution institution = institution(1L, "BoK", "한국은행", WALLET_ADDRESS);
        ContractAddress existingContract =
                ContractAddress.builder()
                        .institution(institution)
                        .name(ContractType.CBDC)
                        .address("0x0000000000000000000000000000000000000001")
                        .build();

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(contractAddressRepository.findByInstitutionIdAndName(1L, ContractType.CBDC))
                .willReturn(Optional.of(existingContract));

        assertThatThrownBy(() -> institutionDeployService.deploy(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.CONTRACT_ALREADY_DEPLOYED);

        verify(contractAddressRepository).findByInstitutionIdAndName(1L, ContractType.CBDC);
    }

    @Test
    @DisplayName("요청 name이 없으면 일반 기관은 DEPOSIT_TOKEN 배포 대상으로 판단한다")
    void deployDefaultDepositTokenForBank() {
        Institution institution = institution(2L, "WOORI", "우리은행", WALLET_ADDRESS);
        ContractAddress existingContract =
                ContractAddress.builder()
                        .institution(institution)
                        .name(ContractType.DEPOSIT_TOKEN)
                        .address("0x0000000000000000000000000000000000000002")
                        .build();

        given(institutionRepository.findById(2L)).willReturn(Optional.of(institution));
        given(contractAddressRepository.findByInstitutionIdAndName(2L, ContractType.DEPOSIT_TOKEN))
                .willReturn(Optional.of(existingContract));

        assertThatThrownBy(() -> institutionDeployService.deploy(2L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.CONTRACT_ALREADY_DEPLOYED);

        verify(contractAddressRepository)
                .findByInstitutionIdAndName(2L, ContractType.DEPOSIT_TOKEN);
    }

    @Test
    @DisplayName("요청 name이 있으면 기관 코드보다 요청 값을 우선한다")
    void deployRequestContractTypeFirst() {
        Institution institution = institution(1L, "BoK", "한국은행", WALLET_ADDRESS);
        ContractAddress existingContract =
                ContractAddress.builder()
                        .institution(institution)
                        .name(ContractType.CONTRACT)
                        .address("0x0000000000000000000000000000000000000003")
                        .build();

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(contractAddressRepository.findByInstitutionIdAndName(1L, ContractType.CONTRACT))
                .willReturn(Optional.of(existingContract));

        assertThatThrownBy(
                        () ->
                                institutionDeployService.deploy(
                                        1L, new DeployContractRequest(ContractType.CONTRACT)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.CONTRACT_ALREADY_DEPLOYED);

        verify(contractAddressRepository).findByInstitutionIdAndName(1L, ContractType.CONTRACT);
    }

    @Test
    @DisplayName("기관 지갑 주소와 개인키 주소가 다르면 INVALID_WALLET_KEY 예외를 던진다")
    void deployInvalidWalletKey() {
        Institution institution =
                institution(2L, "WOORI", "우리은행", "0x0000000000000000000000000000000000000002");

        given(institutionRepository.findById(2L)).willReturn(Optional.of(institution));
        given(contractAddressRepository.findByInstitutionIdAndName(2L, ContractType.DEPOSIT_TOKEN))
                .willReturn(Optional.empty());
        given(walletKeyCipher.decryptCredentials(PRIVATE_KEY))
                .willReturn(Credentials.create(PRIVATE_KEY));

        assertThatThrownBy(() -> institutionDeployService.deploy(2L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INVALID_WALLET_KEY);
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
