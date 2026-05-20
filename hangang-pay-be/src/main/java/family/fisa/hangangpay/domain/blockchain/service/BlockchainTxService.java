package family.fisa.hangangpay.domain.blockchain.service;

import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

@Service
public class BlockchainTxService {

    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;

    @Value("${blockchain.private-network.chain-id}")
    private long privateNetworkChainId;

    /** ABI 함수 객체를 사용해 트랜잭션을 생성, 서명하고 전송합니다. */
    public TransactionReceipt sendFunctionTransaction(
            Web3j web3j,
            Credentials credentials,
            String contractAddress,
            BigInteger gasLimit,
            Function function)
            throws IOException {

        RawTransactionManager transactionManager =
                new RawTransactionManager(web3j, credentials, privateNetworkChainId);

        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(
                                credentials.getAddress(), DefaultBlockParameterName.PENDING)
                        .send();

        if (nonceResponse.hasError()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }

        RawTransaction transaction =
                RawTransaction.createTransaction(
                        nonceResponse.getTransactionCount(),
                        PRIVATE_NETWORK_GAS_PRICE,
                        gasLimit,
                        contractAddress,
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

    /** 트랜잭션 해시로 블록체인 영수증을 폴링하여 반환합니다. */
    public TransactionReceipt waitForReceipt(Web3j web3j, String transactionHash) {
        try {
            PollingTransactionReceiptProcessor processor =
                    new PollingTransactionReceiptProcessor(
                            web3j, RECEIPT_POLLING_INTERVAL_MS, RECEIPT_POLLING_ATTEMPTS);
            return processor.waitForTransactionReceipt(transactionHash);
        } catch (IOException | TransactionException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_RECEIPT_TIMEOUT);
        }
    }
}
