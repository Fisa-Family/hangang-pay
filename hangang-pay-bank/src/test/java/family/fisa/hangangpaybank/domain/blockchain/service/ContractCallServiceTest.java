package family.fisa.hangangpaybank.domain.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.Contract;
import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.ContractRepository;
import family.fisa.hangangpaybank.domain.institution.service.WalletKeyCipher;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class ContractCallServiceTest {

    private static final String PRIVATE_KEY =
            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
    private static final String WALLET_ADDRESS = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    private static final String RPC_ENDPOINT = "http://localhost:8545";
    private static final String LOCAL_CURRENCY_ADDRESS =
            "0x0000000000000000000000000000000000000001";
    private static final String USER_ADDRESS = "0x0000000000000000000000000000000000000002";

    @Mock private ContractRepository contractRepository;
    @Mock private WalletKeyCipher walletKeyCipher;

    @Spy @InjectMocks private ContractCallService contractCallService;

    @Test
    @DisplayName("충전 호출 시 LocalCurrency owner 기관의 credentials와 컨트랙트 주소로 트랜잭션을 보낸다")
    void chargeUsesOwnerInstitutionSigningInfo() throws Exception {
        Institution ownerInstitution = ownerInstitution();
        Contract localCurrency =
                Contract.builder()
                        .name(ContractType.LOCAL_CURRENCY)
                        .address(LOCAL_CURRENCY_ADDRESS)
                        .institution(ownerInstitution)
                        .build();
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        TransactionReceipt receipt = new TransactionReceipt();

        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.of(localCurrency));
        given(walletKeyCipher.decryptCredentials(PRIVATE_KEY)).willReturn(credentials);
        doReturn(receipt)
                .when(contractCallService)
                .sendFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        any(Function.class));

        TransactionReceipt result =
                contractCallService.charge(3L, USER_ADDRESS, BigInteger.valueOf(10_000));

        assertThat(result).isSameAs(receipt);

        ArgumentCaptor<Function> functionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(contractCallService)
                .sendFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        functionCaptor.capture());

        assertThat(functionCaptor.getValue().getName()).isEqualTo("charge");
    }

    @Test
    @DisplayName("대상 컨트랙트가 배포되지 않았으면 예외를 던진다")
    void throwsWhenContractNotDeployed() {
        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                contractCallService.pay(
                                        USER_ADDRESS,
                                        "0x0000000000000000000000000000000000000003",
                                        BigInteger.valueOf(10_000)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_CONTRACT_NOT_DEPLOYED);
    }

    private static Institution ownerInstitution() {
        return Institution.builder()
                .id(1L)
                .institutionCode("BoK")
                .institutionName("한국은행")
                .walletAddress(WALLET_ADDRESS)
                .encryptedPrivateKey(PRIVATE_KEY)
                .rpcEndpoint(RPC_ENDPOINT)
                .build();
    }
}
