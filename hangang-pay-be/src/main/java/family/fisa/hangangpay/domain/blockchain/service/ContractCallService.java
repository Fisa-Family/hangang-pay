package family.fisa.hangangpay.domain.blockchain.service;

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

    private static final BigInteger PAY_GAS_LIMIT = BigInteger.valueOf(300_000);

    private static final BigInteger CANCEL_PAYMENT_GAS_LIMIT = BigInteger.valueOf(300_000);

    private static final BigInteger CHARGE_GAS_LIMIT = BigInteger.valueOf(300_000);

    private static final BigInteger REFUND_GAS_LIMIT = BigInteger.valueOf(300_000);

    private static final BigInteger SET_MERCHANT_GAS_LIMIT = BigInteger.valueOf(100_000);

    private final BlockchainTxService blockchainTxService;

    /*
     * 지역화폐 결제.
     */
    public TransactionReceipt pay(
            Web3j web3j,
            Credentials credentials,
            String localCurrencyAddress,
            String from,
            String to,
            BigInteger amount)
            throws IOException {

        Function function =
                new Function(
                        "pay",
                        List.of(new Address(from), new Address(to), new Uint256(amount)),
                        List.of());

        return blockchainTxService.sendFunctionTransaction(
                web3j, credentials, localCurrencyAddress, PAY_GAS_LIMIT, function);
    }

    /*
     * 결제 취소.
     */
    public TransactionReceipt cancelPayment(
            Web3j web3j,
            Credentials credentials,
            String localCurrencyAddress,
            String from,
            String to,
            BigInteger amount)
            throws IOException {

        Function function =
                new Function(
                        "cancelPayment",
                        List.of(new Address(from), new Address(to), new Uint256(amount)),
                        List.of());

        return blockchainTxService.sendFunctionTransaction(
                web3j, credentials, localCurrencyAddress, CANCEL_PAYMENT_GAS_LIMIT, function);
    }

    /*
     * 충전.
     */
    public TransactionReceipt charge(
            Web3j web3j,
            Credentials credentials,
            String settlementAddress,
            Long institutionId,
            String userAddress,
            BigInteger amount)
            throws IOException {

        Function function =
                new Function(
                        "charge",
                        List.of(
                                new Uint256(BigInteger.valueOf(institutionId)),
                                new Address(userAddress),
                                new Uint256(amount)),
                        List.of());

        return blockchainTxService.sendFunctionTransaction(
                web3j, credentials, settlementAddress, CHARGE_GAS_LIMIT, function);
    }

    /*
     * 환불.
     */
    public TransactionReceipt refund(
            Web3j web3j,
            Credentials credentials,
            String settlementAddress,
            Long institutionId,
            String userAddress,
            BigInteger amount)
            throws IOException {

        Function function =
                new Function(
                        "refund",
                        List.of(
                                new Uint256(BigInteger.valueOf(institutionId)),
                                new Address(userAddress),
                                new Uint256(amount)),
                        List.of());

        return blockchainTxService.sendFunctionTransaction(
                web3j, credentials, settlementAddress, REFUND_GAS_LIMIT, function);
    }

    /*
     * 가맹점 등록/해제.
     */
    public TransactionReceipt setMerchant(
            Web3j web3j,
            Credentials credentials,
            String localCurrencyAddress,
            String merchantAddress,
            boolean approved)
            throws IOException {

        Function function =
                new Function(
                        "setMerchant",
                        List.of(new Address(merchantAddress), new Bool(approved)),
                        List.of());

        return blockchainTxService.sendFunctionTransaction(
                web3j, credentials, localCurrencyAddress, SET_MERCHANT_GAS_LIMIT, function);
    }
}
