package family.fisa.hangangpaybank.domain.blockchain.service;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.SubmittedBlockchainTx;
import family.fisa.hangangpaybank.domain.institution.entity.Contract;
import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.ContractRepository;
import family.fisa.hangangpaybank.domain.institution.service.WalletKeyCipher;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

/**
 * 컨트랙트 호출 전용 서비스 - pay, cancelPayment, charge, refund 메서드 제공.
 *
 * <p>각 호출은 LOCAL_CURRENCY 컨트랙트의 owner institution credentials로 서명하여 동기 전송.
 */
@Service
@RequiredArgsConstructor
public class ContractCallService {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(300_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;
    private final BlockchainTransactionKeyConverter keyConverter;

    // 컨트랙트 커스텀 에러를 BusinessException으로 변환하기 위한 selector -> error code 맵
    private static final Map<String, BlockchainErrorCode> CUSTOM_ERROR_MAP =
            Map.ofEntries(
                    Map.entry(
                            selector("Unauthorized()"),
                            BlockchainErrorCode.BLOCKCHAIN_UNAUTHORIZED),
                    Map.entry(
                            selector("InvalidAddress()"),
                            BlockchainErrorCode.BLOCKCHAIN_INVALID_ADDRESS),
                    Map.entry(
                            selector("InvalidAmount()"),
                            BlockchainErrorCode.BLOCKCHAIN_INVALID_AMOUNT),
                    Map.entry(
                            selector("InvalidInstitutionId()"),
                            BlockchainErrorCode.BLOCKCHAIN_INVALID_INSTITUTION_ID),
                    Map.entry(
                            selector("MerchantNotRegistered()"),
                            BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED),
                    Map.entry(
                            selector("ERC20InsufficientBalance(address,uint256,uint256)"),
                            BlockchainErrorCode.BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE),
                    Map.entry(
                            selector("IssuanceLimitExceeded()"),
                            BlockchainErrorCode.BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED),
                    Map.entry(
                            selector("InsufficientReserve()"),
                            BlockchainErrorCode.BLOCKCHAIN_INSUFFICIENT_RESERVE),
                    Map.entry(
                            selector("ReserveExceedsLockedCbdc()"),
                            BlockchainErrorCode.BLOCKCHAIN_RESERVE_EXCEEDS_LOCKED_CBDC),
                    Map.entry(
                            selector("ReserveMoveFailed()"),
                            BlockchainErrorCode.BLOCKCHAIN_RESERVE_MOVE_FAILED),
                    Map.entry(
                            selector("DepositTokenMintFailed()"),
                            BlockchainErrorCode.BLOCKCHAIN_DEPOSIT_TOKEN_MINT_FAILED),
                    Map.entry(
                            selector("DepositTokenBurnFailed()"),
                            BlockchainErrorCode.BLOCKCHAIN_DEPOSIT_TOKEN_BURN_FAILED),
                    Map.entry(
                            selector("TransferFailed()"),
                            BlockchainErrorCode.BLOCKCHAIN_TRANSFER_FAILED),
                    Map.entry(
                            selector("BankNotRegistered()"),
                            BlockchainErrorCode.BLOCKCHAIN_BANK_NOT_REGISTERED));

    private static String selector(String signature) {
        return Hash.sha3String(signature).substring(0, 10); // 0x + 4 bytes
    }

    @Value("${blockchain.private-network.chain-id:1337}")
    private long privateNetworkChainId;

    private final ContractRepository contractRepository;
    private final WalletKeyCipher walletKeyCipher;

    public TransactionReceipt charge(Long institutionId, String userAddress, BigInteger amount) {
        Function function =
                new Function(
                        "charge",
                        List.of(
                                new Uint256(BigInteger.valueOf(institutionId)),
                                new Address(userAddress),
                                new Uint256(amount)),
                        List.of());
        return sendContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    public TransactionReceipt refund(Long institutionId, String userAddress, BigInteger amount) {
        Function function =
                new Function(
                        "refund",
                        List.of(
                                new Uint256(BigInteger.valueOf(institutionId)),
                                new Address(userAddress),
                                new Uint256(amount)),
                        List.of());
        return sendContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    public TransactionReceipt pay(String from, String to, BigInteger amount) {
        Function function =
                new Function(
                        "pay",
                        List.of(new Address(from), new Address(to), new Uint256(amount)),
                        List.of());
        return sendContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    public TransactionReceipt cancelPayment(String from, String to, BigInteger amount) {
        Function function =
                new Function(
                        "cancelPayment",
                        List.of(new Address(from), new Address(to), new Uint256(amount)),
                        List.of());
        return sendContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    public BigInteger getBalance(String walletAddress) {
        Contract contract =
                contractRepository
                        .findFirstByNameOrderByIdAsc(ContractType.DEPOSIT_TOKEN)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND));
        Institution owner = contract.getInstitution();
        Web3j web3j = Web3j.build(new HttpService(owner.getRpcEndpoint()));

        try {
            return readBalance(
                    web3j, owner.getWalletAddress(), contract.getAddress(), walletAddress);
        } catch (IOException e) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        } finally {
            web3j.shutdown();
        }
    }

    BigInteger readBalance(
            Web3j web3j, String fromAddress, String contractAddress, String walletAddress)
            throws IOException {
        Function function =
                new Function(
                        "balanceOf",
                        List.of(new Address(walletAddress)),
                        List.of(new TypeReference<Uint256>() {}));

        Transaction callTx =
                Transaction.createEthCallTransaction(
                        fromAddress, contractAddress, FunctionEncoder.encode(function));
        EthCall ethCall = web3j.ethCall(callTx, DefaultBlockParameterName.LATEST).send();

        if (ethCall.hasError()) {
            throwCustomErrorIfMatched(ethCall.getError().getData());
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        List<org.web3j.abi.datatypes.Type> decoded =
                FunctionReturnDecoder.decode(ethCall.getValue(), function.getOutputParameters());
        if (decoded.isEmpty()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
        return (BigInteger) decoded.get(0).getValue();
    }

    public boolean isMerchant(String walletAddress) {
        Contract contract =
                contractRepository
                        .findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND));
        Institution owner = contract.getInstitution();
        Web3j web3j = Web3j.build(new HttpService(owner.getRpcEndpoint()));
        try {
            return readMerchant(
                    web3j, owner.getWalletAddress(), contract.getAddress(), walletAddress);
        } catch (IOException e) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        } finally {
            web3j.shutdown();
        }
    }

    boolean readMerchant(
            Web3j web3j, String fromAddress, String contractAddress, String walletAddress)
            throws IOException {
        Function function =
                new Function(
                        "merchants",
                        List.of(new Address(walletAddress)),
                        List.of(new TypeReference<Bool>() {}));

        Transaction callTx =
                Transaction.createEthCallTransaction(
                        fromAddress, contractAddress, FunctionEncoder.encode(function));
        EthCall ethCall = web3j.ethCall(callTx, DefaultBlockParameterName.LATEST).send();

        if (ethCall.hasError()) {
            throwCustomErrorIfMatched(ethCall.getError().getData());
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        List<org.web3j.abi.datatypes.Type> decoded =
                FunctionReturnDecoder.decode(ethCall.getValue(), function.getOutputParameters());
        if (decoded.isEmpty()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
        return (Boolean) decoded.get(0).getValue();
    }

    /** 가맹점 화이트리스트 등록 */
    public TransactionReceipt setMerchant(String merchantAddress) {
        Function function =
                new Function(
                        "setMerchant",
                        List.of(new Address(merchantAddress), new Bool(true)),
                        List.of());

        return sendContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    private TransactionReceipt sendContractFunction(
            ContractType contractType, BigInteger gasLimit, Function function) {
        Contract contract =
                contractRepository
                        .findFirstByNameOrderByIdAsc(contractType)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND));
        Institution owner = contract.getInstitution();
        Credentials credentials =
                walletKeyCipher.decryptCredentials(owner.getEncryptedPrivateKey());
        Web3j web3j = Web3j.build(new HttpService(owner.getRpcEndpoint()));
        try {
            return sendFunctionTransaction(
                    web3j, credentials, contract.getAddress(), gasLimit, function);
        } catch (IOException e) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        } finally {
            web3j.shutdown();
        }
    }

    public TransactionReceipt sendFunctionTransaction(
            Web3j web3j,
            Credentials credentials,
            String contractAddress,
            BigInteger gasLimit,
            Function function)
            throws IOException {

        simulateOrThrow(web3j, credentials.getAddress(), contractAddress, gasLimit, function);

        RawTransactionManager mgr =
                new RawTransactionManager(web3j, credentials, privateNetworkChainId);
        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(
                                credentials.getAddress(), DefaultBlockParameterName.PENDING)
                        .send();
        if (nonceResponse.hasError()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
        RawTransaction tx =
                RawTransaction.createTransaction(
                        nonceResponse.getTransactionCount(),
                        PRIVATE_NETWORK_GAS_PRICE,
                        gasLimit,
                        contractAddress,
                        BigInteger.ZERO,
                        FunctionEncoder.encode(function));
        EthSendTransaction sendResponse = mgr.signAndSend(tx);
        if (sendResponse.hasError()) {
            String errorData = sendResponse.getError().getData();
            throwCustomErrorIfMatched(errorData);
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());
        if (!receipt.isStatusOK()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_REVERTED);
        }
        return receipt;
    }

    public TransactionReceipt waitForReceipt(Web3j web3j, String txHash) {
        try {
            PollingTransactionReceiptProcessor processor =
                    new PollingTransactionReceiptProcessor(
                            web3j, RECEIPT_POLLING_INTERVAL_MS, RECEIPT_POLLING_ATTEMPTS);
            return processor.waitForTransactionReceipt(txHash);
        } catch (IOException | TransactionException e) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RECEIPT_TIMEOUT);
        }
    }

    /** submit* : 블록체인에 요청 제출 후 txHash 반환, receipt를 기다리지 않음. */
    public SubmittedBlockchainTx submitPayment(
            String transactionUuid, String from, String to, BigInteger amount) {
        byte[] txKey = keyConverter.toBytes32(transactionUuid);
        Function function =
                new Function(
                        "pay",
                        List.of(
                                new Bytes32(txKey),
                                new Address(from),
                                new Address(to),
                                new Uint256(amount)),
                        List.of());
        return submitContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    public SubmittedBlockchainTx submitCancelPayment(
            String transactionUuid, String from, String to, BigInteger amount) {
        byte[] txKey = keyConverter.toBytes32(transactionUuid);
        Function function =
                new Function(
                        "cancelPayment",
                        List.of(
                                new Bytes32(txKey),
                                new Address(from),
                                new Address(to),
                                new Uint256(amount)),
                        List.of());
        return submitContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    /**
     * 지정된 컨트랙트 함수 호출 트랜잭션을 제출하고 트랜잭션 해시를 반환한다. 트랜잭션 영수증을 기다리지 않고 제출까지만 수행하며, 결과 확인은 반환된 txHash를 통해
     * 별도로 조회해야 한다.
     */
    private SubmittedBlockchainTx submitContractFunction(
            ContractType contractType, BigInteger gasLimit, Function function) {

        // 1. 호출 대상 컨트랙트 조회
        Contract contract =
                contractRepository
                        .findFirstByNameOrderByIdAsc(contractType)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND));

        // 2. 컨트랙트 owner 기관 정보 조회
        Institution owner = contract.getInstitution();

        // 3. 기관 개인키를 복호화하여 서명 계정 생성
        Credentials credentials =
                walletKeyCipher.decryptCredentials(owner.getEncryptedPrivateKey());

        // 4. 가관 RPC 엔드포인트로 Web3j 클라이언트 생성
        Web3j web3j = Web3j.build(new HttpService(owner.getRpcEndpoint()));

        try {
            // 컨트랙트 함수 호출 및 txHash 반환
            return submitFunctionTransaction(
                    web3j, credentials, contract.getAddress(), gasLimit, function);
        } catch (IOException e) {
            // RPC 통신 예외 반환
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        } finally {
            // Web3j 리소스 정리
            web3j.shutdown();
        }
    }

    /** 실제 트랜잭션을 전송. 컨트랙트 함수를 비동기 방식으로 제출하고 트랜잭션 해시를 반환한다. */
    SubmittedBlockchainTx submitFunctionTransaction(
            Web3j web3j,
            Credentials credentials,
            String contractAddress,
            BigInteger gasLimit,
            Function function)
            throws IOException {
        // 1. 실제 트랜잭션 전송 전에 컨트랙트 함수를 시뮬레이션 실행
        simulateOrThrow(web3j, credentials.getAddress(), contractAddress, gasLimit, function);

        // 2. 트랜잭션 서명 및 전송을 위한 RawTransactionManager 생성
        RawTransactionManager mgr =
                new RawTransactionManager(web3j, credentials, privateNetworkChainId);

        // 3. Pending 상태를 기준으로 현재 계정의 nonce 조회
        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(
                                credentials.getAddress(), DefaultBlockParameterName.PENDING)
                        .send();

        // 4. nonce 조회 실패 시 RPC 예외 처리
        if (nonceResponse.hasError()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        // 5. 컨트랙트 함수 호출용 RawTransaction 생성
        RawTransaction tx =
                RawTransaction.createTransaction(
                        nonceResponse.getTransactionCount(),
                        PRIVATE_NETWORK_GAS_PRICE,
                        gasLimit,
                        contractAddress,
                        BigInteger.ZERO,
                        FunctionEncoder.encode(function));

        // 6. 트랜잭션 서명 후 네트워크에 전송
        EthSendTransaction sendResponse = mgr.signAndSend(tx);

        // 7. 전송 실패 시 custom error 매핑 후 예외 처리
        if (sendResponse.hasError()) {
            throwCustomErrorIfMatched(sendResponse.getError().getData());
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        // 8. 트랜잭션 해시 반환
        return new SubmittedBlockchainTx(sendResponse.getTransactionHash());
    }

    /**
     * 실제 트랜잭션 전송 전에 eth_call로 컨트랙트 함수를 시뮬레이션 실행한다.
     *
     * <p>트랜잭션을 블록에 포함시키기 전에 컨트랙트 실행 결과를 미리 확인하여 revert 여부와 custom error를 감지하기 위한 용도이다.
     *
     * <p>컨트랙트에서 custom error가 발생하면 selector를 추출하여 대응되는 BlockchainErrorCode로 변환한다.
     *
     * <p>일반 revert(Error(string)) 발생 시 BLOCKCHAIN_TRANSACTION_REVERTED 예외를 발생시킨다.
     */
    private void simulateOrThrow(
            Web3j web3j,
            String from,
            String contractAddress,
            BigInteger gasLimit,
            Function function)
            throws IOException {

        // 컨트랙트 함수 호출 데이터를 ABI 인코딩
        String data = FunctionEncoder.encode(function);

        // 상태 변경 없이 실행되는 eth_call 트랜잭션 생성
        Transaction callTx =
                Transaction.createFunctionCallTransaction(
                        from,
                        null,
                        PRIVATE_NETWORK_GAS_PRICE,
                        gasLimit,
                        contractAddress,
                        BigInteger.ZERO,
                        data);

        // 최신 블록 상태 기준으로 함수 시뮬레이션 실행
        EthCall ethCall = web3j.ethCall(callTx, DefaultBlockParameterName.LATEST).send();

        // RPC 레벨 에러 발생 시 custom error 매핑 시도
        if (ethCall.hasError()) {
            String errorData = ethCall.getError().getData();
            throwCustomErrorIfMatched(errorData);

            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_REVERTED);
        }

        String value = ethCall.getValue();

        // Error(string) 형태의 일반 revert
        // 0x08c379a0 = Error(string) selector
        if (value != null && value.startsWith("0x08c379a0")) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_REVERTED);
        }

        // Custom Error selector 검사
        if (value != null && value.length() >= 10) {
            throwCustomErrorIfMatched(value);
        }
    }

    /**
     * revert 데이터의 selector를 추출하여 등록된 컨트랙트 custom error와 매칭되는 경우 대응되는 BusinessException을 발생시킨다.
     *
     * <p>예: MerchantNotRegistered() → BLOCKCHAIN_MERCHANT_NOT_REGISTERED
     */
    private void throwCustomErrorIfMatched(String revertData) {
        if (revertData == null || revertData.length() < 10) {
            return;
        }

        // 0x + 4byte selector
        String selector = revertData.substring(0, 10);

        BlockchainErrorCode code = CUSTOM_ERROR_MAP.get(selector);

        if (code != null) {
            throw new BusinessException(code);
        }
    }
}
