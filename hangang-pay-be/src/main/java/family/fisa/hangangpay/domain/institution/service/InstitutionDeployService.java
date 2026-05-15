package family.fisa.hangangpay.domain.institution.service;

import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.dto.DeployAllContractsResponse;
import family.fisa.hangangpay.domain.institution.dto.DeployContractResponse;
import family.fisa.hangangpay.domain.institution.entity.ContractAddress;
import family.fisa.hangangpay.domain.institution.entity.ContractType;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.ContractAddressRepository;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
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
    private static final BigInteger SET_BANK_GAS_LIMIT = BigInteger.valueOf(200_000);
    private static final BigInteger SET_OPERATOR_GAS_LIMIT = BigInteger.valueOf(100_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;
    private static final long PRIVATE_NETWORK_CHAIN_ID = 1337L;
    private static final String CBDC_INSTITUTION_CODE = "BoK";

    private final TokenArtifactLoader tokenArtifactLoader;
    private final WalletKeyCipher walletKeyCipher;
    private final InstitutionRepository institutionRepository;
    private final ContractAddressRepository contractAddressRepository;

    /*
     * BoK CBDC, 은행별 DepositToken, BoK Settlement 순서로 전체 배포한다.
     */
    @Transactional
    public DeployAllContractsResponse deployAll() {
        Institution centralBank =
                institutionRepository
                        .findByInstitutionCode(CBDC_INSTITUTION_CODE)
                        .orElseThrow(() -> new BusinessException(InstitutionErrorCode.NOT_FOUND));

        List<DeployContractResponse> responses = new ArrayList<>();

        responses.add(deploy(centralBank, ContractType.CBDC));

        for (Institution institution : institutionRepository.findAllByOrderByIdAsc()) {
            if (isCentralBank(institution)) {
                continue;
            }

            responses.add(deploy(institution, ContractType.DEPOSIT_TOKEN));
        }

        responses.add(deploy(centralBank, ContractType.CONTRACT));

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
                                    InstitutionErrorCode.CONTRACT_ALREADY_DEPLOYED);
                        });

        Credentials credentials =
                walletKeyCipher.decryptCredentials(institution.getEncryptedPrivateKey());
        validateSignerAddress(institution, credentials);

        Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

        try {
            RawTransactionManager transactionManager =
                    new RawTransactionManager(web3j, credentials, PRIVATE_NETWORK_CHAIN_ID);

            TransactionReceipt receipt =
                    deployContract(
                            web3j,
                            transactionManager,
                            credentials.getAddress(),
                            contractType,
                            institution);

            ContractAddress contractAddress =
                    contractAddressRepository.save(
                            ContractAddress.builder()
                                    .institution(institution)
                                    .name(contractType)
                                    .address(receipt.getContractAddress())
                                    .build());

            if (contractType == ContractType.CONTRACT) {
                registerSettlementAsOperator(contractAddress.getAddress());
                registerBanksInSettlement(contractAddress.getAddress(), institution);
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
            throw new BusinessException(InstitutionErrorCode.BLOCKCHAIN_RPC_FAILED);
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
            ContractType contractType,
            Institution institution)
            throws IOException {
        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(signerAddress, DefaultBlockParameterName.PENDING)
                        .send();

        if (nonceResponse.hasError()) {
            throw new BusinessException(InstitutionErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        RawTransaction deployTransaction =
                RawTransaction.createContractTransaction(
                        nonceResponse.getTransactionCount(),
                        PRIVATE_NETWORK_GAS_PRICE,
                        DEPLOY_GAS_LIMIT,
                        BigInteger.ZERO,
                        resolveBytecode(contractType, institution));

        EthSendTransaction sendResponse = transactionManager.signAndSend(deployTransaction);

        if (sendResponse.hasError()) {
            throw new BusinessException(InstitutionErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

        if (!receipt.isStatusOK()) {
            throw new BusinessException(InstitutionErrorCode.TRANSACTION_REVERTED);
        }

        if (receipt.getContractAddress() == null || receipt.getContractAddress().isBlank()) {
            throw new BusinessException(InstitutionErrorCode.DEPLOYMENT_RECEIPT_MISSING);
        }

        return receipt;
    }

    /*
     * Settlement 컨트랙트에 은행별 DepositToken과 준비금 지갑을 등록한다.
     */
    private void registerBanksInSettlement(String settlementAddress, Institution centralBank)
            throws IOException {

        Credentials credentials =
                walletKeyCipher.decryptCredentials(centralBank.getEncryptedPrivateKey());

        Web3j web3j = Web3j.build(new HttpService(centralBank.getRpcEndpoint()));

        try {
            RawTransactionManager transactionManager =
                    new RawTransactionManager(web3j, credentials, PRIVATE_NETWORK_CHAIN_ID);

            for (ContractAddress contractAddress :
                    contractAddressRepository.findAllByName(ContractType.DEPOSIT_TOKEN)) {

                Institution bank = contractAddress.getInstitution();

                callSetBank(
                        web3j,
                        transactionManager,
                        credentials.getAddress(),
                        settlementAddress,
                        bank.getId(),
                        contractAddress.getAddress(),
                        bank.getWalletAddress());
            }
        } finally {
            web3j.shutdown();
        }
    }

    private TransactionReceipt callSetBank(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String signerAddress,
            String settlementAddress,
            Long institutionId,
            String tokenAddress,
            String reserveWallet)
            throws IOException {
        Function function =
                new Function(
                        "setBank",
                        List.of(
                                new Uint256(BigInteger.valueOf(institutionId)),
                                new Address(tokenAddress),
                                new Address(reserveWallet)),
                        List.of());

        return sendFunctionTransaction(
                web3j,
                transactionManager,
                signerAddress,
                settlementAddress,
                SET_BANK_GAS_LIMIT,
                function);
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
                                ownerWeb3j, ownerCredentials, PRIVATE_NETWORK_CHAIN_ID);

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
     * setBank, setOperator 같은 컨트랙트 함수 호출 트랜잭션 공통 처리.
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
            throw new BusinessException(InstitutionErrorCode.BLOCKCHAIN_RPC_FAILED);
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
            throw new BusinessException(InstitutionErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

        if (!receipt.isStatusOK()) {
            throw new BusinessException(InstitutionErrorCode.TRANSACTION_REVERTED);
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
            throw new BusinessException(InstitutionErrorCode.RECEIPT_TIMEOUT);
        }
    }

    private static void validateSignerAddress(Institution institution, Credentials credentials) {
        if (!institution.getWalletAddress().equalsIgnoreCase(credentials.getAddress())) {
            throw new BusinessException(InstitutionErrorCode.INVALID_WALLET_KEY);
        }
    }

    private static void validateInstitutionDeploymentInfo(Institution institution) {
        if (institution.getWalletAddress() == null || institution.getWalletAddress().isBlank()) {
            throw new BusinessException(InstitutionErrorCode.MISSING_DEPLOYMENT_INFO);
        }

        if (institution.getEncryptedPrivateKey() == null
                || institution.getEncryptedPrivateKey().isBlank()) {
            throw new BusinessException(InstitutionErrorCode.MISSING_DEPLOYMENT_INFO);
        }

        if (institution.getRpcEndpoint() == null || institution.getRpcEndpoint().isBlank()) {
            throw new BusinessException(InstitutionErrorCode.MISSING_DEPLOYMENT_INFO);
        }
    }

    /*
     * 컨트랙트 종류별 artifact bytecode와 생성자 인자를 조합한다.
     */
    private String resolveBytecode(ContractType contractType, Institution institution) {
        return switch (contractType) {
            case CBDC -> tokenArtifactLoader.cbdcArtifact().bytecode();
            case DEPOSIT_TOKEN ->
                    appendConstructorArgs(
                            tokenArtifactLoader.depositTokenArtifact().bytecode(),
                            List.of(
                                    new Uint256(BigInteger.valueOf(institution.getId())),
                                    new Utf8String(institution.getInstitutionName()),
                                    new Utf8String("BANK" + institution.getId())));
            case CONTRACT ->
                    appendConstructorArgs(
                            tokenArtifactLoader.settlementArtifact().bytecode(),
                            List.of(
                                    new Address(
                                            resolveContractAddress(
                                                    CBDC_INSTITUTION_CODE, ContractType.CBDC))));
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

    private static boolean isCentralBank(Institution institution) {
        return CBDC_INSTITUTION_CODE.equalsIgnoreCase(institution.getInstitutionCode());
    }

    private String resolveContractAddress(String institutionCode, ContractType contractType) {
        return contractAddressRepository
                .findByInstitutionInstitutionCodeAndName(institutionCode, contractType)
                .map(ContractAddress::getAddress)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.CONTRACT_NOT_DEPLOYED));
    }
}
