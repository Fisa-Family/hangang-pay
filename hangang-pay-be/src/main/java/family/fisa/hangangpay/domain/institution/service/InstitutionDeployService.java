package family.fisa.hangangpay.domain.institution.service;

import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.dto.DeployAllContractsResponse;
import family.fisa.hangangpay.domain.institution.dto.DeployContractResponse;
import family.fisa.hangangpay.domain.institution.entity.ContractAddress;
import family.fisa.hangangpay.domain.institution.entity.ContractType;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.entity.InstitutionCode;
import family.fisa.hangangpay.domain.institution.repository.ContractAddressRepository;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
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
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

/*
 * 기관별 컨트랙트 배포와 배포 후 초기 설정을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class InstitutionDeployService {

    private static final BigInteger DEPLOY_GAS_LIMIT = BigInteger.valueOf(4_000_000);
    private static final BigInteger SET_OPERATOR_GAS_LIMIT = BigInteger.valueOf(100_000);
    private static final BigInteger REGISTER_BANK_GAS_LIMIT = BigInteger.valueOf(100_000);
    private static final BigInteger SET_RESERVE_GAS_LIMIT = BigInteger.valueOf(150_000);
    private static final BigInteger MINT_GAS_LIMIT = BigInteger.valueOf(150_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;

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
    private final ContractAddressRepository contractAddressRepository;

    /*
     * BoK CBDC, 우리은행 DepositToken, Settlement, LocalCurrencyPolicy 순서로 전체 배포한다.
     */
    @Transactional
    public DeployAllContractsResponse deployAll() {
        Institution centralBank =
                institutionRepository
                        .findByInstitutionCode(InstitutionCode.BOK.getCode())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        Institution wooriBank =
                institutionRepository
                        .findByInstitutionCode(InstitutionCode.WOORI.getCode())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        List<DeployContractResponse> responses = new ArrayList<>();

        // 1. CBDC 배포
        responses.add(deploy(centralBank, ContractType.CBDC));

        // 2. 우리은행 DepositToken 배포
        responses.add(deploy(wooriBank, ContractType.DEPOSIT_TOKEN));

        // 3. Settlement 배포
        responses.add(deploy(centralBank, ContractType.CONTRACT));

        // 4. LocalCurrencyPolicy 배포
        responses.add(deploy(centralBank, ContractType.LOCAL_CURRENCY));

        return new DeployAllContractsResponse(responses);
    }

    /*
     * 기관 정보와 중복 배포 여부를 검증한 뒤 배포 주소를 저장한다.
     */
    private DeployContractResponse deploy(Institution institution, ContractType contractType) {
        validateInstitutionDeploymentInfo(institution);

        contractAddressRepository
                .findByInstitutionIdAndName(institution.getId(), contractType)
                .ifPresent(
                        existing -> {
                            throw new BusinessException(
                                    InstitutionErrorCode.INSTITUTION_CONTRACT_ALREADY_DEPLOYED);
                        });

        Credentials credentials =
                walletKeyCipher.decryptCredentials(institution.getEncryptedPrivateKey());
        validateSignerAddress(institution, credentials);

        Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

        try {
            RawTransactionManager transactionManager =
                    new RawTransactionManager(web3j, credentials, privateNetworkChainId);

            TransactionReceipt receipt =
                    deployContract(
                            web3j, transactionManager, credentials.getAddress(), contractType);

            ContractAddress contractAddress =
                    contractAddressRepository.save(
                            ContractAddress.builder()
                                    .institution(institution)
                                    .name(contractType)
                                    .address(receipt.getContractAddress())
                                    .build());
            if (contractType == ContractType.CONTRACT) {
                registerSettlementAsOperator(contractAddress.getAddress());
                mintCbdcToSettlement(contractAddress.getAddress());
                registerBanks(contractAddress.getAddress(), institution);
                setInitialReserves(contractAddress.getAddress(), institution);
            }
            if (contractType == ContractType.LOCAL_CURRENCY) {
                registerLocalCurrencyAsOperator(contractAddress.getAddress());
            }

            return new DeployContractResponse(
                    institution.getId(),
                    institution.getInstitutionName(),
                    contractAddress.getName(),
                    contractAddress.getAddress(),
                    receipt.getTransactionHash(),
                    institution.getRpcEndpoint(),
                    credentials.getAddress());
        } catch (IOException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        } finally {
            web3j.shutdown();
        }
    }

    /*
     * Hardhat artifact bytecode로 컨트랙트 생성 트랜잭션을 전송한다.
     */
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

        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

        if (!receipt.isStatusOK()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_TRANSACTION_REVERTED);
        }

        if (receipt.getContractAddress() == null || receipt.getContractAddress().isBlank()) {
            throw new BusinessException(
                    InstitutionErrorCode.INSTITUTION_DEPLOYMENT_RECEIPT_MISSING);
        }

        return receipt;
    }

    /*
     * Settlement가 CBDC/DepositToken의 mint, burn, forceTransfer를 호출할 수 있도록
     * operator 권한을 부여한다.
     */
    private void registerSettlementAsOperator(String settlementAddress) throws IOException {
        for (ContractAddress contractAddress :
                contractAddressRepository.findAllByNameIn(
                        List.of(ContractType.CBDC, ContractType.DEPOSIT_TOKEN))) {

            Institution ownerInstitution = contractAddress.getInstitution();
            Credentials ownerCredentials =
                    walletKeyCipher.decryptCredentials(ownerInstitution.getEncryptedPrivateKey());

            Web3j ownerWeb3j = Web3j.build(new HttpService(ownerInstitution.getRpcEndpoint()));

            try {
                RawTransactionManager ownerTransactionManager =
                        new RawTransactionManager(
                                ownerWeb3j, ownerCredentials, privateNetworkChainId);

                callSetOperator(
                        ownerWeb3j,
                        ownerTransactionManager,
                        ownerCredentials.getAddress(),
                        contractAddress.getAddress(),
                        settlementAddress);
            } finally {
                ownerWeb3j.shutdown();
            }
        }
    }

    private void registerLocalCurrencyAsOperator(String localCurrencyAddress) throws IOException {

        ContractAddress depositToken =
                contractAddressRepository
                        .findByInstitutionInstitutionCodeAndName(
                                InstitutionCode.WOORI.getCode(), ContractType.DEPOSIT_TOKEN)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode
                                                        .INSTITUTION_CONTRACT_NOT_DEPLOYED));

        Institution ownerInstitution = depositToken.getInstitution();

        Credentials credentials =
                walletKeyCipher.decryptCredentials(ownerInstitution.getEncryptedPrivateKey());

        Web3j web3j = Web3j.build(new HttpService(ownerInstitution.getRpcEndpoint()));

        try {
            RawTransactionManager transactionManager =
                    new RawTransactionManager(web3j, credentials, privateNetworkChainId);

            callSetOperator(
                    web3j,
                    transactionManager,
                    credentials.getAddress(),
                    depositToken.getAddress(),
                    localCurrencyAddress);

        } finally {
            web3j.shutdown();
        }
    }

    private TransactionReceipt callSetOperator(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String signerAddress,
            String tokenAddress,
            String operatorAddress)
            throws IOException {
        Function function =
                new Function(
                        "setOperator",
                        List.of(new Address(operatorAddress), new Bool(true)),
                        List.of());

        return sendFunctionTransaction(
                web3j,
                transactionManager,
                signerAddress,
                tokenAddress,
                SET_OPERATOR_GAS_LIMIT,
                function);
    }

    /*
     * setOperator, registerBank, setReserve, mint 같은 컨트랙트 함수 호출 트랜잭션 공통 처리.
     */
    private TransactionReceipt sendFunctionTransaction(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String signerAddress,
            String toAddress,
            BigInteger gasLimit,
            Function function)
            throws IOException {
        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(signerAddress, DefaultBlockParameterName.PENDING)
                        .send();

        if (nonceResponse.hasError()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }

        RawTransaction transaction =
                RawTransaction.createTransaction(
                        nonceResponse.getTransactionCount(),
                        PRIVATE_NETWORK_GAS_PRICE,
                        gasLimit,
                        toAddress,
                        BigInteger.ZERO,
                        FunctionEncoder.encode(function));

        EthSendTransaction sendResponse = transactionManager.signAndSend(transaction);

        if (sendResponse.hasError()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }

        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

        if (!receipt.isStatusOK()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_TRANSACTION_REVERTED);
        }

        return receipt;
    }

    private TransactionReceipt waitForReceipt(Web3j web3j, String transactionHash) {
        try {
            PollingTransactionReceiptProcessor processor =
                    new PollingTransactionReceiptProcessor(
                            web3j, RECEIPT_POLLING_INTERVAL_MS, RECEIPT_POLLING_ATTEMPTS);

            return processor.waitForTransactionReceipt(transactionHash);
        } catch (IOException | TransactionException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_RECEIPT_TIMEOUT);
        }
    }

    private static void validateSignerAddress(Institution institution, Credentials credentials) {
        if (!institution.getWalletAddress().equalsIgnoreCase(credentials.getAddress())) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_INVALID_WALLET_KEY);
        }
    }

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
     * 컨트랙트 종류별 artifact bytecode와 생성자 인자를 조합한다.
     */
    private String resolveBytecode(ContractType contractType) {
        return switch (contractType) {
            case CBDC -> tokenArtifactLoader.cbdcArtifact().bytecode();
            case DEPOSIT_TOKEN -> tokenArtifactLoader.depositTokenArtifact().bytecode();
            case CONTRACT ->
                    appendConstructorArgs(
                            tokenArtifactLoader.settlementArtifact().bytecode(),
                            List.of(
                                    new Address(
                                            resolveContractAddress(
                                                    InstitutionCode.BOK.getCode(),
                                                    ContractType.CBDC)),
                                    new Address(
                                            resolveContractAddress(
                                                    InstitutionCode.WOORI.getCode(),
                                                    ContractType.DEPOSIT_TOKEN))));
            case LOCAL_CURRENCY ->
                    appendConstructorArgs(
                            tokenArtifactLoader.localCurrencyArtifact().bytecode(),
                            List.of(
                                    new Address(
                                            resolveContractAddress(
                                                    InstitutionCode.WOORI.getCode(),
                                                    ContractType.DEPOSIT_TOKEN)),
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

    private String resolveContractAddress(String institutionCode, ContractType contractType) {
        return contractAddressRepository
                .findByInstitutionInstitutionCodeAndName(institutionCode, contractType)
                .map(ContractAddress::getAddress)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        InstitutionErrorCode.INSTITUTION_CONTRACT_NOT_DEPLOYED));
    }

    private TransactionReceipt callRegisterBank(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String signerAddress,
            String settlementAddress,
            Long institutionId)
            throws IOException {
        Function function =
                new Function(
                        "registerBank",
                        List.of(new Uint256(BigInteger.valueOf(institutionId))),
                        List.of());

        return sendFunctionTransaction(
                web3j,
                transactionManager,
                signerAddress,
                settlementAddress,
                REGISTER_BANK_GAS_LIMIT,
                function);
    }

    private TransactionReceipt callSetReserve(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String signerAddress,
            String settlementAddress,
            Long institutionId,
            BigInteger amount)
            throws IOException {
        Function function =
                new Function(
                        "setReserve",
                        List.of(
                                new Uint256(BigInteger.valueOf(institutionId)),
                                new Uint256(amount)),
                        List.of());

        return sendFunctionTransaction(
                web3j,
                transactionManager,
                signerAddress,
                settlementAddress,
                SET_RESERVE_GAS_LIMIT,
                function);
    }

    private TransactionReceipt callMint(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String signerAddress,
            String tokenAddress,
            String toAddress,
            BigInteger amount)
            throws IOException {
        Function function =
                new Function(
                        "mint", List.of(new Address(toAddress), new Uint256(amount)), List.of());

        return sendFunctionTransaction(
                web3j, transactionManager, signerAddress, tokenAddress, MINT_GAS_LIMIT, function);
    }

    /*
     * BoK가 CBDC를 발행해 Settlement 컨트랙트에 lock한다.
     * 이후 Settlement 내부 reserveBalance로 기관별 CBDC reserve를 관리한다.
     */
    private void mintCbdcToSettlement(String settlementAddress) throws IOException {
        Institution centralBank =
                institutionRepository
                        .findByInstitutionCode(InstitutionCode.BOK.getCode())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        String cbdcAddress =
                resolveContractAddress(InstitutionCode.BOK.getCode(), ContractType.CBDC);

        Credentials credentials =
                walletKeyCipher.decryptCredentials(centralBank.getEncryptedPrivateKey());

        Web3j web3j = Web3j.build(new HttpService(centralBank.getRpcEndpoint()));

        try {
            RawTransactionManager transactionManager =
                    new RawTransactionManager(web3j, credentials, privateNetworkChainId);

            BigInteger amount = INITIAL_LOCKED_CBDC_AMOUNT;

            callMint(
                    web3j,
                    transactionManager,
                    credentials.getAddress(),
                    cbdcAddress,
                    settlementAddress,
                    amount);
        } finally {
            web3j.shutdown();
        }
    }

    /*
     * Settlement 컨트랙트에 모든 기관 ID를 등록한다.
     * 등록된 기관만 reserve 배정 및 충전/환불 정산에 참여할 수 있다.
     */
    private void registerBanks(String settlementAddress, Institution centralBank)
            throws IOException {
        Credentials credentials =
                walletKeyCipher.decryptCredentials(centralBank.getEncryptedPrivateKey());

        Web3j web3j = Web3j.build(new HttpService(centralBank.getRpcEndpoint()));

        try {
            RawTransactionManager transactionManager =
                    new RawTransactionManager(web3j, credentials, privateNetworkChainId);

            for (Institution institution : institutionRepository.findAllByOrderByIdAsc()) {
                callRegisterBank(
                        web3j,
                        transactionManager,
                        credentials.getAddress(),
                        settlementAddress,
                        institution.getId());
            }
        } finally {
            web3j.shutdown();
        }
    }

    /*
     * 기관별 초기 CBDC reserve를 배정한다.
     * 실제 CBDC는 Settlement 컨트랙트에 lock되어 있고,
     * 이 함수는 Settlement 내부 장부 reserveBalance를 설정한다.
     */
    private void setInitialReserves(String settlementAddress, Institution centralBank)
            throws IOException {
        Credentials credentials =
                walletKeyCipher.decryptCredentials(centralBank.getEncryptedPrivateKey());

        Web3j web3j = Web3j.build(new HttpService(centralBank.getRpcEndpoint()));

        try {
            RawTransactionManager transactionManager =
                    new RawTransactionManager(web3j, credentials, privateNetworkChainId);

            for (Institution institution : institutionRepository.findAllByOrderByIdAsc()) {
                BigInteger amount = INITIAL_BANK_RESERVE_AMOUNT;

                callSetReserve(
                        web3j,
                        transactionManager,
                        credentials.getAddress(),
                        settlementAddress,
                        institution.getId(),
                        amount);
            }
        } finally {
            web3j.shutdown();
        }
    }
}
