package family.fisa.hangangpay.domain.transfer.service;

import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.entity.ContractType;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.entity.InstitutionCode;
import family.fisa.hangangpay.domain.institution.repository.ContractAddressRepository;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.domain.institution.service.WalletKeyCipher;
import family.fisa.hangangpay.domain.transfer.code.error.ChargeErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
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

/** 충전 블록체인 호출 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeBlockchainService {

    /** mint 트랜잭션 가스 한도 */
    private static final BigInteger MINT_GAS_LIMIT = BigInteger.valueOf(200_000);
    /** private 네트워크 가스 가격 */
    private static final BigInteger GAS_PRICE = BigInteger.ZERO;
    /** receipt 폴링 최대 시도 횟수 */
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    /** receipt 폴링 간격 (ms) */
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;

    @Value("${blockchain.private-network.chain-id}")
    private long chainId;

    private final WalletKeyCipher walletKeyCipher;
    private final ContractAddressRepository contractAddressRepository;
    private final InstitutionRepository institutionRepository;

    /** 은행의 CBDC 준비금 조회 */
    public BigInteger getCbdcReserve(Institution bank) {
        Institution bok =
                institutionRepository
                        .findByInstitutionCode(InstitutionCode.BOK.getCode())
                        .orElseThrow(
                                () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        String cbdcAddress =
                contractAddressRepository
                        .findByInstitutionIdAndName(bok.getId(), ContractType.CBDC)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_CONTRACT_NOT_DEPLOYED))
                        .getAddress();

        Web3j web3j = Web3j.build(new HttpService(bank.getRpcEndpoint()));
        try {
            BigInteger reserve = balanceOf(web3j, cbdcAddress, bank.getWalletAddress());
            log.info(
                    "CBDC 준비금 조회: bank={}, walletAddress={}, reserve={}",
                    bank.getInstitutionName(),
                    bank.getWalletAddress(),
                    reserve);
            return reserve;
        } finally {
            web3j.shutdown();
        }
    }

    /** 은행 DepositToken totalSupply 조회 */
    public BigInteger getDepositTokenTotalSupply(Institution bank) {
        String depositTokenAddress =
                contractAddressRepository
                        .findByInstitutionIdAndName(bank.getId(), ContractType.DEPOSIT_TOKEN)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_CONTRACT_NOT_DEPLOYED))
                        .getAddress();

        Web3j web3j = Web3j.build(new HttpService(bank.getRpcEndpoint()));
        try {
            BigInteger supply = totalSupply(web3j, depositTokenAddress);
            log.info(
                    "DepositToken totalSupply 조회: bank={}, supply={}",
                    bank.getInstitutionName(),
                    supply);
            return supply;
        } finally {
            web3j.shutdown();
        }
    }

    /** DepositToken mint 트랜잭션 전송 후 영수증 반환, 실패 시 MINT_FAILED */
    public TransactionReceipt mint(Institution bank, String userWalletAddress, BigInteger amount) {
        String depositTokenAddress =
                contractAddressRepository
                        .findByInstitutionIdAndName(bank.getId(), ContractType.DEPOSIT_TOKEN)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_CONTRACT_NOT_DEPLOYED))
                        .getAddress();

        Credentials credentials = walletKeyCipher.decryptCredentials(bank.getEncryptedPrivateKey());
        Web3j web3j = Web3j.build(new HttpService(bank.getRpcEndpoint()));
        try {
            log.info(
                    "mint 호출 시작: bank={}, depositToken={}, to={}, amount={}",
                    bank.getInstitutionName(),
                    depositTokenAddress,
                    userWalletAddress,
                    amount);
            TransactionReceipt receipt =
                    sendMintTransaction(web3j, credentials, depositTokenAddress, userWalletAddress, amount);
            log.info("mint 완료: txHash={}", receipt.getTransactionHash());
            return receipt;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("mint 실패: bank={}, to={}, amount={}", bank.getInstitutionName(), userWalletAddress, amount, e);
            throw new BusinessException(ChargeErrorCode.MINT_FAILED);
        } finally {
            web3j.shutdown();
        }
    }

    /** 특정 주소의 토큰 잔액 조회 */
    private BigInteger balanceOf(Web3j web3j, String contractAddress, String holder) {
        Function fn =
                new Function(
                        "balanceOf",
                        List.of(new Address(holder)),
                        List.of(new TypeReference<Uint256>() {}));
        try {
            EthCall result =
                    web3j.ethCall(
                                    Transaction.createEthCallTransaction(
                                            null, contractAddress, FunctionEncoder.encode(fn)),
                                    DefaultBlockParameterName.LATEST)
                            .send();
            if (result.hasError()) {
                throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
            }
            List<Type> decoded =
                    FunctionReturnDecoder.decode(result.getValue(), fn.getOutputParameters());
            return ((Uint256) decoded.get(0)).getValue();
        } catch (IOException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }
    }

    /** 토큰 총 발행량 조회 */
    private BigInteger totalSupply(Web3j web3j, String contractAddress) {
        Function fn =
                new Function(
                        "totalSupply", List.of(), List.of(new TypeReference<Uint256>() {}));
        try {
            EthCall result =
                    web3j.ethCall(
                                    Transaction.createEthCallTransaction(
                                            null, contractAddress, FunctionEncoder.encode(fn)),
                                    DefaultBlockParameterName.LATEST)
                            .send();
            if (result.hasError()) {
                throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
            }
            List<Type> decoded =
                    FunctionReturnDecoder.decode(result.getValue(), fn.getOutputParameters());
            return ((Uint256) decoded.get(0)).getValue();
        } catch (IOException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        }
    }

    /** mint 트랜잭션 서명·전송 및 receipt 대기 */
    private TransactionReceipt sendMintTransaction(
            Web3j web3j,
            Credentials credentials,
            String depositTokenAddress,
            String to,
            BigInteger amount)
            throws IOException, TransactionException {

        Function fn =
                new Function(
                        "mint", List.of(new Address(to), new Uint256(amount)), List.of());

        EthGetTransactionCount nonceResponse =
                web3j.ethGetTransactionCount(
                                credentials.getAddress(), DefaultBlockParameterName.PENDING)
                        .send();
        if (nonceResponse.hasError()) {
            throw new BusinessException(ChargeErrorCode.MINT_FAILED);
        }

        RawTransaction tx =
                RawTransaction.createTransaction(
                        nonceResponse.getTransactionCount(),
                        GAS_PRICE,
                        MINT_GAS_LIMIT,
                        depositTokenAddress,
                        BigInteger.ZERO,
                        FunctionEncoder.encode(fn));

        RawTransactionManager txManager =
                new RawTransactionManager(web3j, credentials, chainId);
        EthSendTransaction sendResponse = txManager.signAndSend(tx);
        if (sendResponse.hasError()) {
            throw new BusinessException(ChargeErrorCode.MINT_FAILED);
        }

        PollingTransactionReceiptProcessor processor =
                new PollingTransactionReceiptProcessor(
                        web3j, RECEIPT_POLLING_INTERVAL_MS, RECEIPT_POLLING_ATTEMPTS);
        TransactionReceipt receipt =
                processor.waitForTransactionReceipt(sendResponse.getTransactionHash());

        if (!receipt.isStatusOK()) {
            throw new BusinessException(ChargeErrorCode.MINT_FAILED);
        }
        return receipt;
    }
}
