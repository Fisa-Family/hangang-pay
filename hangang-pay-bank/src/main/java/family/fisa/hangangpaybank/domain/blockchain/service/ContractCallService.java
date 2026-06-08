package family.fisa.hangangpaybank.domain.blockchain.service;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
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
@Slf4j
public class ContractCallService {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(300_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;
    private static final int MAX_LOG_DATA_LENGTH = 300;

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
            log.info(
                    "[blockchain] balanceOf call start. walletAddress={}, contractAddress={}, institutionCode={}, rpcEndpoint={}",
                    walletAddress,
                    contract.getAddress(),
                    owner.getInstitutionCode(),
                    owner.getRpcEndpoint());
            BigInteger balance =
                    readBalance(
                            web3j, owner.getWalletAddress(), contract.getAddress(), walletAddress);
            log.info(
                    "[blockchain] balanceOf call success. walletAddress={}, contractAddress={}, institutionCode={}, balance={}",
                    walletAddress,
                    contract.getAddress(),
                    owner.getInstitutionCode(),
                    balance);
            return balance;
        } catch (IOException e) {
            log.error(
                    "[blockchain] balanceOf IO failure. walletAddress={}, contractAddress={}, institutionCode={}, rpcEndpoint={}",
                    walletAddress,
                    contract.getAddress(),
                    owner.getInstitutionCode(),
                    owner.getRpcEndpoint(),
                    e);
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        } catch (BusinessException e) {
            log.warn(
                    "[blockchain] balanceOf business failure. walletAddress={}, contractAddress={}, institutionCode={}, errorCode={}, errorMessage={}",
                    walletAddress,
                    contract.getAddress(),
                    owner.getInstitutionCode(),
                    e.getCode().getCode(),
                    e.getCode().getMessage(),
                    e);
            throw e;
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
            log.warn(
                    "[blockchain] balanceOf RPC error. walletAddress={}, contractAddress={}, errorCode={}, errorMessage={}, errorData={}",
                    walletAddress,
                    contractAddress,
                    rpcErrorCode(ethCall.getError()),
                    rpcErrorMessage(ethCall.getError()),
                    compactLogData(rpcErrorData(ethCall.getError())));
            throwCustomErrorIfMatched(rpcErrorData(ethCall.getError()));
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }

        List<org.web3j.abi.datatypes.Type> decoded =
                FunctionReturnDecoder.decode(ethCall.getValue(), function.getOutputParameters());
        if (decoded.isEmpty()) {
            log.warn(
                    "[blockchain] balanceOf decode empty. walletAddress={}, contractAddress={}, rawValue={}",
                    walletAddress,
                    contractAddress,
                    compactLogData(ethCall.getValue()));
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
        return (BigInteger) decoded.get(0).getValue();
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
            log.info(
                    "[blockchain] contract tx start. functionName={}, contractType={}, contractAddress={}, institutionCode={}, signerAddress={}, rpcEndpoint={}, chainId={}, gasLimit={}",
                    function.getName(),
                    contractType,
                    contract.getAddress(),
                    owner.getInstitutionCode(),
                    credentials.getAddress(),
                    owner.getRpcEndpoint(),
                    privateNetworkChainId,
                    gasLimit);
            TransactionReceipt receipt =
                    sendFunctionTransaction(
                            web3j, credentials, contract.getAddress(), gasLimit, function);
            log.info(
                    "[blockchain] contract tx success. functionName={}, contractType={}, contractAddress={}, txHash={}, blockNumber={}, status={}",
                    function.getName(),
                    contractType,
                    contract.getAddress(),
                    safeReceiptTxHash(receipt),
                    safeReceiptBlockNumber(receipt),
                    safeReceiptStatus(receipt));
            return receipt;
        } catch (IOException e) {
            log.error(
                    "[blockchain] contract tx IO failure. functionName={}, contractType={}, contractAddress={}, institutionCode={}, signerAddress={}, rpcEndpoint={}",
                    function.getName(),
                    contractType,
                    contract.getAddress(),
                    owner.getInstitutionCode(),
                    credentials.getAddress(),
                    owner.getRpcEndpoint(),
                    e);
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        } catch (BusinessException e) {
            log.warn(
                    "[blockchain] contract tx business failure. functionName={}, contractType={}, contractAddress={}, institutionCode={}, signerAddress={}, rpcEndpoint={}, errorCode={}, errorMessage={}",
                    function.getName(),
                    contractType,
                    contract.getAddress(),
                    owner.getInstitutionCode(),
                    credentials.getAddress(),
                    owner.getRpcEndpoint(),
                    e.getCode().getCode(),
                    e.getCode().getMessage(),
                    e);
            throw e;
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

        log.info(
                "[blockchain] eth_call simulation start. functionName={}, contractAddress={}, signerAddress={}, gasLimit={}",
                function.getName(),
                contractAddress,
                credentials.getAddress(),
                gasLimit);
        simulateOrThrow(web3j, credentials.getAddress(), contractAddress, gasLimit, function);
        log.info(
                "[blockchain] eth_call simulation success. functionName={}, contractAddress={}, signerAddress={}",
                function.getName(),
                contractAddress,
                credentials.getAddress());

        RawTransactionManager mgr =
                new RawTransactionManager(web3j, credentials, privateNetworkChainId);
        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(
                                credentials.getAddress(), DefaultBlockParameterName.PENDING)
                        .send();
        if (nonceResponse.hasError()) {
            log.warn(
                    "[blockchain] eth_getTransactionCount RPC error. functionName={}, signerAddress={}, contractAddress={}, errorCode={}, errorMessage={}, errorData={}",
                    function.getName(),
                    credentials.getAddress(),
                    contractAddress,
                    rpcErrorCode(nonceResponse.getError()),
                    rpcErrorMessage(nonceResponse.getError()),
                    compactLogData(rpcErrorData(nonceResponse.getError())));
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
        log.info(
                "[blockchain] nonce fetched. functionName={}, signerAddress={}, contractAddress={}, nonce={}",
                function.getName(),
                credentials.getAddress(),
                contractAddress,
                nonceResponse.getTransactionCount());
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
            String errorData = rpcErrorData(sendResponse.getError());
            log.warn(
                    "[blockchain] eth_sendRawTransaction RPC error. functionName={}, signerAddress={}, contractAddress={}, nonce={}, chainId={}, errorCode={}, errorMessage={}, errorData={}",
                    function.getName(),
                    credentials.getAddress(),
                    contractAddress,
                    nonceResponse.getTransactionCount(),
                    privateNetworkChainId,
                    rpcErrorCode(sendResponse.getError()),
                    rpcErrorMessage(sendResponse.getError()),
                    compactLogData(errorData));
            throwCustomErrorIfMatched(errorData);
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
        log.info(
                "[blockchain] transaction submitted. functionName={}, signerAddress={}, contractAddress={}, txHash={}",
                function.getName(),
                credentials.getAddress(),
                contractAddress,
                sendResponse.getTransactionHash());
        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());
        if (!receipt.isStatusOK()) {
            log.warn(
                    "[blockchain] transaction receipt failed. functionName={}, contractAddress={}, txHash={}, status={}, blockNumber={}",
                    function.getName(),
                    contractAddress,
                    safeReceiptTxHash(receipt),
                    safeReceiptStatus(receipt),
                    safeReceiptBlockNumber(receipt));
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_REVERTED);
        }
        return receipt;
    }

    public TransactionReceipt waitForReceipt(Web3j web3j, String txHash) {
        try {
            log.info(
                    "[blockchain] receipt polling start. txHash={}, intervalMs={}, attempts={}",
                    txHash,
                    RECEIPT_POLLING_INTERVAL_MS,
                    RECEIPT_POLLING_ATTEMPTS);
            PollingTransactionReceiptProcessor processor =
                    new PollingTransactionReceiptProcessor(
                            web3j, RECEIPT_POLLING_INTERVAL_MS, RECEIPT_POLLING_ATTEMPTS);
            TransactionReceipt receipt = processor.waitForTransactionReceipt(txHash);
            log.info(
                    "[blockchain] receipt polling success. txHash={}, status={}, blockNumber={}",
                    txHash,
                    safeReceiptStatus(receipt),
                    safeReceiptBlockNumber(receipt));
            return receipt;
        } catch (IOException | TransactionException e) {
            log.error("[blockchain] receipt polling failure. txHash={}", txHash, e);
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RECEIPT_TIMEOUT);
        }
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
            String errorData = rpcErrorData(ethCall.getError());
            log.warn(
                    "[blockchain] eth_call simulation RPC error. functionName={}, from={}, contractAddress={}, errorCode={}, errorMessage={}, errorData={}",
                    function.getName(),
                    from,
                    contractAddress,
                    rpcErrorCode(ethCall.getError()),
                    rpcErrorMessage(ethCall.getError()),
                    compactLogData(errorData));
            throwCustomErrorIfMatched(errorData);

            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_REVERTED);
        }

        String value = ethCall.getValue();

        // Error(string) 형태의 일반 revert
        // 0x08c379a0 = Error(string) selector
        if (value != null && value.startsWith("0x08c379a0")) {
            log.warn(
                    "[blockchain] eth_call simulation reverted with Error(string). functionName={}, from={}, contractAddress={}, rawValue={}",
                    function.getName(),
                    from,
                    contractAddress,
                    compactLogData(value));
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
            log.warn(
                    "[blockchain] contract custom error matched. selector={}, errorCode={}, errorData={}",
                    selector,
                    code.getCode(),
                    compactLogData(revertData));
            throw new BusinessException(code);
        }
    }

    private static Integer rpcErrorCode(Response.Error error) {
        return error != null ? error.getCode() : null;
    }

    private static String rpcErrorMessage(Response.Error error) {
        return error != null ? error.getMessage() : null;
    }

    private static String rpcErrorData(Response.Error error) {
        return error != null ? error.getData() : null;
    }

    private static String compactLogData(String value) {
        if (value == null || value.length() <= MAX_LOG_DATA_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_DATA_LENGTH) + "...";
    }

    private static String safeReceiptStatus(TransactionReceipt receipt) {
        try {
            return receipt.getStatus();
        } catch (RuntimeException e) {
            return "unavailable:" + e.getClass().getSimpleName();
        }
    }

    private static String safeReceiptTxHash(TransactionReceipt receipt) {
        try {
            return receipt.getTransactionHash();
        } catch (RuntimeException e) {
            return "unavailable:" + e.getClass().getSimpleName();
        }
    }

    private static String safeReceiptBlockNumber(TransactionReceipt receipt) {
        try {
            BigInteger blockNumber = receipt.getBlockNumber();
            return blockNumber != null ? blockNumber.toString() : null;
        } catch (RuntimeException e) {
            return "unavailable:" + e.getClass().getSimpleName();
        }
    }
}
