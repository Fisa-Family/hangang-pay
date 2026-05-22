package family.fisa.hangangpaybank.domain.institution.service;

import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.dto.response.DeployAllContractsResponse;
import family.fisa.hangangpaybank.domain.institution.dto.response.DeployContractResponse;
import family.fisa.hangangpaybank.domain.institution.entity.Contract;
import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.entity.InstitutionCode;
import family.fisa.hangangpaybank.domain.institution.repository.ContractRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;

/*
 * 기관별 컨트랙트 배포와 배포 후 초기 설정을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class InstitutionDeployService {

    private static final BigInteger DEPLOY_GAS_LIMIT = BigInteger.valueOf(4_000_000);
    private static final BigInteger CONTRACT_CALL_GAS_LIMIT = BigInteger.valueOf(300_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;

    // ERC20 기본 decimals 기준
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    // Settlement 컨트랙트에 lock할 전체 CBDC 수량
    private static final BigInteger INITIAL_LOCKED_CBDC_AMOUNT =
            BigInteger.valueOf(1_000_000_000L).multiply(TOKEN_DECIMALS);

    // 기관별 초기 CBDC reserve 배정 수량
    private static final BigInteger INITIAL_BANK_RESERVE_AMOUNT =
            BigInteger.valueOf(100_000_000L).multiply(TOKEN_DECIMALS);

    // 지역화폐 총 사용 한도
    private static final BigInteger LOCAL_CURRENCY_MAX_TOTAL_USAGE =
            BigInteger.valueOf(1_000_000L).multiply(TOKEN_DECIMALS);

    @Value("${blockchain.private-network.chain-id}")
    private long privateNetworkChainId;

    private final TokenArtifactLoader tokenArtifactLoader;
    private final WalletKeyCipher walletKeyCipher;
    private final InstitutionRepository institutionRepository;
    private final ContractRepository contractAddressRepository;
    private final ContractCallService contractCallService;

    /*
     * BoK CBDC, 우리은행 DepositToken, Settlement, LocalCurrencyPolicy 순서로 전체 배포한다.
     */
    @Transactional
    public DeployAllContractsResponse deployAll() {
        Institution centralBank = findInstitution(InstitutionCode.BOK);
        Institution wooriBank = findInstitution(InstitutionCode.WOORI);

        List<DeployContractResponse> responses =
                List.of(
                        deploy(centralBank, ContractType.CBDC),
                        deploy(wooriBank, ContractType.DEPOSIT_TOKEN),
                        deploy(centralBank, ContractType.SETTLEMENT),
                        deploy(centralBank, ContractType.LOCAL_CURRENCY));

        return new DeployAllContractsResponse(responses);
    }

    /** 특정 기관에 대해 한 건의 컨트랙트 배포를 수행하고 DB에 주소를 저장합니다. */
    private DeployContractResponse deploy(Institution institution, ContractType contractType) {
        validateInstitutionDeploymentInfo(institution);

        contractAddressRepository
                .findByInstitutionIdAndName(institution.getId(), contractType)
                .ifPresent(
                        existing -> {
                            throw new BusinessException(
                                    InstitutionErrorCode.INSTITUTION_CONTRACT_ALREADY_DEPLOYED);
                        });

        Credentials credentials = credentialsFor(institution);
        validateSignerAddress(institution, credentials);

        try {
            return withWeb3j(
                    institution,
                    credentials,
                    (web3j, signerCredentials) -> {
                        RawTransactionManager transactionManager =
                                new RawTransactionManager(
                                        web3j, signerCredentials, privateNetworkChainId);

                        TransactionReceipt receipt =
                                deployContract(
                                        web3j,
                                        transactionManager,
                                        signerCredentials.getAddress(),
                                        contractType);

                        Contract contract =
                                contractAddressRepository.save(
                                        Contract.builder()
                                                .institution(institution)
                                                .name(contractType)
                                                .address(receipt.getContractAddress())
                                                .build());
                        if (contractType == ContractType.SETTLEMENT) {
                            mintCbdcToSettlement(contract.getAddress());
                            registerBanks(contract.getAddress(), institution);
                            setInitialReserves(contract.getAddress(), institution);
                        }
                        if (contractType == ContractType.LOCAL_CURRENCY) {
                            registerLocalCurrencyAsOperator(contract.getAddress());
                        }

                        return new DeployContractResponse(
                                institution.getId(),
                                institution.getInstitutionName(),
                                contract.getName(),
                                contract.getAddress(),
                                receipt.getTransactionHash(),
                                institution.getRpcEndpoint(),
                                signerCredentials.getAddress());
                    });
        } catch (IOException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }
    }

    /** Hardhat bytecode를 이용해 실제 컨트랙트 생성 트랜잭션을 전송합니다. */
    private TransactionReceipt deployContract(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String signerAddress,
            ContractType contractType)
            throws IOException {
        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(signerAddress, DefaultBlockParameterName.PENDING)
                        .send();

        if (nonceResponse.hasError()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }

        RawTransaction deployTransaction =
                RawTransaction.createContractTransaction(
                        nonceResponse.getTransactionCount(),
                        PRIVATE_NETWORK_GAS_PRICE,
                        DEPLOY_GAS_LIMIT,
                        BigInteger.ZERO,
                        resolveBytecode(contractType));

        EthSendTransaction sendResponse = transactionManager.signAndSend(deployTransaction);

        if (sendResponse.hasError()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }

        TransactionReceipt receipt =
                contractCallService.waitForReceipt(web3j, sendResponse.getTransactionHash());

        if (!receipt.isStatusOK()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_TRANSACTION_REVERTED);
        }

        if (receipt.getContractAddress() == null || receipt.getContractAddress().isBlank()) {
            throw new BusinessException(
                    InstitutionErrorCode.INSTITUTION_DEPLOYMENT_RECEIPT_MISSING);
        }

        return receipt;
    }

    /** 지역화폐 스마트컨트랙트에 operator 권한을 부여합니다. */
    private void registerLocalCurrencyAsOperator(String localCurrencyAddress) throws IOException {
        registerOperator(InstitutionCode.WOORI, ContractType.DEPOSIT_TOKEN, localCurrencyAddress);
        registerOperator(InstitutionCode.BOK, ContractType.SETTLEMENT, localCurrencyAddress);
    }

    private void registerOperator(
            InstitutionCode institutionCode, ContractType contractType, String operatorAddress)
            throws IOException {
        Contract contract = findContract(institutionCode, contractType);

        runWithInstitutionWallet(
                contract.getInstitution(),
                (web3j, credentials) ->
                        callSetOperator(
                                web3j, credentials, contract.getAddress(), operatorAddress));
    }

    /** setOperator 함수 호출을 래핑합니다. */
    private TransactionReceipt callSetOperator(
            Web3j web3j, Credentials credentials, String contractAddress, String operatorAddress)
            throws IOException {
        return sendFunction(
                web3j,
                credentials,
                contractAddress,
                CONTRACT_CALL_GAS_LIMIT,
                "setOperator",
                List.of(new Address(operatorAddress), new Bool(true)));
    }

    /** 저장된 기관 지갑 주소와 복호화된 키의 서명 주소가 일치하는지 확인합니다. */
    private static void validateSignerAddress(Institution institution, Credentials credentials) {
        if (!institution.getWalletAddress().equalsIgnoreCase(credentials.getAddress())) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_INVALID_WALLET_KEY);
        }
    }

    /** 배포에 필요한 기관 정보가 누락되지 않았는지 확인합니다. */
    private static void validateInstitutionDeploymentInfo(Institution institution) {
        if (institution.getWalletAddress() == null || institution.getWalletAddress().isBlank()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_MISSING_DEPLOYMENT_INFO);
        }

        if (institution.getEncryptedPrivateKey() == null
                || institution.getEncryptedPrivateKey().isBlank()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_MISSING_DEPLOYMENT_INFO);
        }

        if (institution.getRpcEndpoint() == null || institution.getRpcEndpoint().isBlank()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_MISSING_DEPLOYMENT_INFO);
        }
    }

    /*
     * 컨트랙트 종류에 맞는 bytecode와 생성자 인자를 결합합니다.
     */
    private String resolveBytecode(ContractType contractType) {
        return switch (contractType) {
            case CBDC -> tokenArtifactLoader.cbdcArtifact().bytecode();
            case DEPOSIT_TOKEN -> tokenArtifactLoader.depositTokenArtifact().bytecode();
            case SETTLEMENT ->
                    appendConstructorArgs(
                            tokenArtifactLoader.settlementArtifact().bytecode(),
                            List.of(
                                    new Address(
                                            resolveContractAddress(
                                                    InstitutionCode.BOK, ContractType.CBDC))));
            case LOCAL_CURRENCY ->
                    appendConstructorArgs(
                            tokenArtifactLoader.localCurrencyArtifact().bytecode(),
                            List.of(
                                    new Address(
                                            resolveContractAddress(
                                                    InstitutionCode.WOORI,
                                                    ContractType.DEPOSIT_TOKEN)),
                                    new Address(
                                            resolveContractAddress(
                                                    InstitutionCode.BOK, ContractType.SETTLEMENT)),
                                    new Uint256(LOCAL_CURRENCY_MAX_TOTAL_USAGE)));
        };
    }

    private static String appendConstructorArgs(String bytecode, List<Type> constructorArgs) {
        return bytecode + stripHexPrefix(FunctionEncoder.encodeConstructor(constructorArgs));
    }

    private static String stripHexPrefix(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return value.substring(2);
        }

        return value;
    }

    private String resolveContractAddress(
            InstitutionCode institutionCode, ContractType contractType) {
        return findContract(institutionCode, contractType).getAddress();
    }

    private Institution findInstitution(InstitutionCode institutionCode) {
        return institutionRepository
                .findByInstitutionCode(institutionCode.getCode())
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));
    }

    private Contract findContract(InstitutionCode institutionCode, ContractType contractType) {
        return contractAddressRepository
                .findByInstitutionInstitutionCodeAndName(institutionCode.getCode(), contractType)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        InstitutionErrorCode.INSTITUTION_CONTRACT_NOT_DEPLOYED));
    }

    private Credentials credentialsFor(Institution institution) {
        return walletKeyCipher.decryptCredentials(institution.getEncryptedPrivateKey());
    }

    private <T> T withInstitutionWallet(Institution institution, Web3jCall<T> call)
            throws IOException {
        return withWeb3j(institution, credentialsFor(institution), call);
    }

    private void runWithInstitutionWallet(Institution institution, Web3jAction action)
            throws IOException {
        withInstitutionWallet(
                institution,
                (web3j, credentials) -> {
                    action.execute(web3j, credentials);
                    return null;
                });
    }

    private <T> T withWeb3j(Institution institution, Credentials credentials, Web3jCall<T> call)
            throws IOException {
        Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

        try {
            return call.execute(web3j, credentials);
        } finally {
            web3j.shutdown();
        }
    }

    /** Settlement에 은행 기관 ID를 등록합니다. */
    private TransactionReceipt callRegisterBank(
            Web3j web3j, Credentials credentials, String settlementAddress, Long institutionId)
            throws IOException {
        return sendFunction(
                web3j,
                credentials,
                settlementAddress,
                CONTRACT_CALL_GAS_LIMIT,
                "registerBank",
                List.of(new Uint256(BigInteger.valueOf(institutionId))));
    }

    /** 기관별 초기 reserve 금액을 Settlement에 설정합니다. */
    private TransactionReceipt callSetReserve(
            Web3j web3j,
            Credentials credentials,
            String settlementAddress,
            Long institutionId,
            BigInteger amount)
            throws IOException {
        return sendFunction(
                web3j,
                credentials,
                settlementAddress,
                CONTRACT_CALL_GAS_LIMIT,
                "setReserve",
                List.of(new Uint256(BigInteger.valueOf(institutionId)), new Uint256(amount)));
    }

    /** 토큰을 지정된 주소로 발행(mint)합니다. */
    private TransactionReceipt callMint(
            Web3j web3j,
            Credentials credentials,
            String tokenAddress,
            String toAddress,
            BigInteger amount)
            throws IOException {
        return sendFunction(
                web3j,
                credentials,
                tokenAddress,
                CONTRACT_CALL_GAS_LIMIT,
                "mint",
                List.of(new Address(toAddress), new Uint256(amount)));
    }

    private TransactionReceipt sendFunction(
            Web3j web3j,
            Credentials credentials,
            String contractAddress,
            BigInteger gasLimit,
            String functionName,
            List<Type> inputParameters)
            throws IOException {
        Function function = new Function(functionName, inputParameters, List.of());

        return contractCallService.sendFunctionTransaction(
                web3j, credentials, contractAddress, gasLimit, function);
    }

    /*
     * BoK가 CBDC를 발행해 Settlement 컨트랙트에 lock한다.
     * 이후 Settlement 내부 reserveBalance로 기관별 CBDC reserve를 관리한다.
     */
    private void mintCbdcToSettlement(String settlementAddress) throws IOException {
        Institution centralBank = findInstitution(InstitutionCode.BOK);
        String cbdcAddress = resolveContractAddress(InstitutionCode.BOK, ContractType.CBDC);

        runWithInstitutionWallet(
                centralBank,
                (web3j, credentials) ->
                        callMint(
                                web3j,
                                credentials,
                                cbdcAddress,
                                settlementAddress,
                                INITIAL_LOCKED_CBDC_AMOUNT));
    }

    /*
     * Settlement 컨트랙트에 모든 기관 ID를 등록한다.
     * 등록된 기관만 reserve 배정 및 충전/환불 정산에 참여할 수 있다.
     */
    private void registerBanks(String settlementAddress, Institution centralBank)
            throws IOException {
        runWithInstitutionWallet(
                centralBank,
                (web3j, credentials) -> {
                    for (Institution institution : institutionRepository.findAllByOrderByIdAsc()) {
                        callRegisterBank(
                                web3j, credentials, settlementAddress, institution.getId());
                    }
                });
    }

    /*
     * 기관별 초기 CBDC reserve를 배정한다.
     * 실제 CBDC는 Settlement 컨트랙트에 lock되어 있고,
     * 이 함수는 Settlement 내부 장부 reserveBalance를 설정한다.
     */
    private void setInitialReserves(String settlementAddress, Institution centralBank)
            throws IOException {
        runWithInstitutionWallet(
                centralBank,
                (web3j, credentials) -> {
                    for (Institution institution : institutionRepository.findAllByOrderByIdAsc()) {
                        callSetReserve(
                                web3j,
                                credentials,
                                settlementAddress,
                                institution.getId(),
                                INITIAL_BANK_RESERVE_AMOUNT);
                    }
                });
    }

    @FunctionalInterface
    private interface Web3jCall<T> {

        T execute(Web3j web3j, Credentials credentials) throws IOException;
    }

    @FunctionalInterface
    private interface Web3jAction {

        void execute(Web3j web3j, Credentials credentials) throws IOException;
    }
}
