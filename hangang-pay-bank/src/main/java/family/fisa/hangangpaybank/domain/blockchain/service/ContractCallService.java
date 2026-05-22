package family.fisa.hangangpaybank.domain.blockchain.service;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.Contract;
import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.ContractRepository;
import family.fisa.hangangpaybank.domain.institution.service.WalletKeyCipher;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
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

    private TransactionReceipt sendContractFunction(
            ContractType contractType, BigInteger gasLimit, Function function) {
        Contract contract =
                contractRepository
                        .findFirstByNameOrderByIdAsc(contractType)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode
                                                        .INSTITUTION_CONTRACT_NOT_DEPLOYED));
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
}
