package family.fisa.hangangpay.domain.blockchain.service;

import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.entity.ContractAddress;
import family.fisa.hangangpay.domain.institution.entity.ContractType;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.ContractAddressRepository;
import family.fisa.hangangpay.domain.institution.service.WalletKeyCipher;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

/*
 * 실제 서비스 도메인에서 사용하는 컨트랙트 호출 전용 서비스.
 *
 * 역할:
 * - pay
 * - cancelPayment
 * - charge
 * - refund
 *
 * 다른 도메인 서비스에서 이 클래스만 호출하면 된다.
 */
@Service
@RequiredArgsConstructor
public class ContractCallService {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(300_000);
    private static final BigInteger SET_MERCHANT_GAS_LIMIT = BigInteger.valueOf(100_000);

    private final BlockchainTxService blockchainTxService;
    private final ContractAddressRepository contractAddressRepository;
    private final WalletKeyCipher walletKeyCipher;

    /*
     * 지역화폐 결제.
     */
    public TransactionReceipt pay(String from, String to, BigInteger amount) {
        Function function =
                new Function(
                        "pay",
                        List.of(new Address(from), new Address(to), new Uint256(amount)),
                        List.of());

        return sendContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    /*
     * 결제 취소.
     */
    public TransactionReceipt cancelPayment(String from, String to, BigInteger amount) {
        Function function =
                new Function(
                        "cancelPayment",
                        List.of(new Address(from), new Address(to), new Uint256(amount)),
                        List.of());

        return sendContractFunction(ContractType.LOCAL_CURRENCY, DEFAULT_GAS_LIMIT, function);
    }

    /*
     * 충전.
     */
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

    /*
     * 환불.
     */
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

    /*
     * 가맹점 등록/해제.
     */
    public TransactionReceipt setMerchant(String merchantAddress, boolean approved) {
        Function function =
                new Function(
                        "setMerchant",
                        List.of(new Address(merchantAddress), new Bool(approved)),
                        List.of());

        return sendContractFunction(ContractType.LOCAL_CURRENCY, SET_MERCHANT_GAS_LIMIT, function);
    }

    /*
     * 컨트랙트 주소와 owner 기관 정보를 조회해 signer/Web3j를 구성하고 트랜잭션을 전송합니다.
     */
    private TransactionReceipt sendContractFunction(
            ContractType contractType, BigInteger gasLimit, Function function) {
        ContractAddress contractAddress = resolveContractAddress(contractType);
        Institution ownerInstitution = contractAddress.getInstitution();

        validateInstitutionSigningInfo(ownerInstitution);

        Credentials credentials =
                walletKeyCipher.decryptCredentials(ownerInstitution.getEncryptedPrivateKey());
        validateSignerAddress(ownerInstitution, credentials);

        Web3j web3j = Web3j.build(new HttpService(ownerInstitution.getRpcEndpoint()));

        try {
            return blockchainTxService.sendFunctionTransaction(
                    web3j, credentials, contractAddress.getAddress(), gasLimit, function);
        } catch (IOException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_BLOCKCHAIN_RPC_FAILED);
        } finally {
            web3j.shutdown();
        }
    }

    private ContractAddress resolveContractAddress(ContractType contractType) {
        return contractAddressRepository
                .findFirstByNameOrderByIdAsc(contractType)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        InstitutionErrorCode.INSTITUTION_CONTRACT_NOT_DEPLOYED));
    }

    private static void validateInstitutionSigningInfo(Institution institution) {
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

    private static void validateSignerAddress(Institution institution, Credentials credentials) {
        if (!institution.getWalletAddress().equalsIgnoreCase(credentials.getAddress())) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_INVALID_WALLET_KEY);
        }
    }
}
