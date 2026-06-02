package family.fisa.hangangpaybank.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class TransactionCommandServiceTest {

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String ACCOUNT_NUMBER = "1002-123-456789";
    private static final String FROM_WALLET = "0x0000000000000000000000000000000000000001";
    private static final String TO_WALLET = "0x0000000000000000000000000000000000000002";
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private BlockchainLedgerRepository blockchainLedgerRepository;
    @Mock private AccountLedgerRepository accountLedgerRepository;
    @Mock private ContractCallService contractCallService;
    @Mock private PaymentStateWriter paymentStateWriter;

    @InjectMocks private TransactionCommandService transactionCommandService;

    @Test
    @DisplayName("충전 완료 응답의 지갑 잔액은 컨트랙트 balanceOf 기준으로 반환한다")
    void chargeReturnsContractWalletBalance() {
        Institution institution = institution();
        BankAccount bankAccount = bankAccount(institution, new BigDecimal("100000"));
        BankWallet bankWallet = bankWallet(institution, TO_WALLET);
        ChargeRequest request =
                new ChargeRequest(
                        TRANSACTION_UUID,
                        1L,
                        ACCOUNT_NUMBER,
                        TO_WALLET,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        TransactionReceipt receipt = receipt("0x-charge", 10L);
        AccountLedger accountLedger = AccountLedger.builder().id(100L).build();

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(bankAccountRepository.findByInstitution_IdAndAccountNumber(1L, ACCOUNT_NUMBER))
                .willReturn(Optional.of(bankAccount));
        given(bankWalletRepository.findByWalletAddress(TO_WALLET))
                .willReturn(Optional.of(bankWallet));
        given(accountLedgerRepository.save(any(AccountLedger.class))).willReturn(accountLedger);
        given(contractCallService.charge(1L, TO_WALLET, toTokenUnit(new BigDecimal("10000"))))
                .willReturn(receipt);
        given(blockchainLedgerRepository.save(any(BlockchainLedger.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(contractCallService.getBalance(TO_WALLET))
                .willReturn(toTokenUnit(new BigDecimal("12345")));

        ChargeResponse response = transactionCommandService.charge(request);

        assertThat(response.walletBalance()).isEqualByComparingTo(new BigDecimal("12345"));
        assertThat(response.txHash()).isEqualTo("0x-charge");
    }

    @Test
    @DisplayName("결제 성공 시 컨트랙트 잔액으로 검증하고 응답 잔액도 컨트랙트 기준으로 넘긴다")
    void paymentUsesContractBalances() {
        Institution institution = institution();
        BankWallet fromWallet = bankWallet(institution, FROM_WALLET);
        BankWallet toWallet = bankWallet(institution, TO_WALLET);
        PaymentRequest request =
                new PaymentRequest(TRANSACTION_UUID, FROM_WALLET, TO_WALLET, new BigDecimal("10"));
        BlockchainLedger pending =
                BlockchainLedger.builder()
                        .id(7L)
                        .institution(institution)
                        .idempotentKey(TRANSACTION_UUID)
                        .status(BlockchainTxStatus.PENDING)
                        .build();
        TransactionReceipt receipt = receipt("0x-pay", 20L);
        PaymentResponse completed =
                new PaymentResponse(
                        TRANSACTION_UUID,
                        7L,
                        "0x-pay",
                        20L,
                        null,
                        new BigDecimal("90"),
                        new BigDecimal("60"));

        given(bankWalletRepository.findByWalletAddress(FROM_WALLET))
                .willReturn(Optional.of(fromWallet));
        given(bankWalletRepository.findByWalletAddress(TO_WALLET))
                .willReturn(Optional.of(toWallet));
        given(paymentStateWriter.findExisting(TRANSACTION_UUID)).willReturn(Optional.empty());
        given(contractCallService.getBalance(FROM_WALLET))
                .willReturn(toTokenUnit(new BigDecimal("100")), toTokenUnit(new BigDecimal("90")));
        given(contractCallService.getBalance(TO_WALLET))
                .willReturn(toTokenUnit(new BigDecimal("60")));
        given(paymentStateWriter.preparePending(TRANSACTION_UUID, institution)).willReturn(pending);
        given(contractCallService.pay(FROM_WALLET, TO_WALLET, toTokenUnit(new BigDecimal("10"))))
                .willReturn(receipt);
        given(
                        paymentStateWriter.completePayment(
                                7L,
                                receipt,
                                TRANSACTION_UUID,
                                new BigDecimal("90"),
                                new BigDecimal("60")))
                .willReturn(completed);

        PaymentResponse response = transactionCommandService.payment(request);

        assertThat(response.fromBalance()).isEqualByComparingTo(new BigDecimal("90"));
        assertThat(response.toBalance()).isEqualByComparingTo(new BigDecimal("60"));
    }

    private static Institution institution() {
        return Institution.builder()
                .id(1L)
                .institutionCode("WOORI")
                .institutionName("Woori Bank")
                .build();
    }

    private static BankAccount bankAccount(Institution institution, BigDecimal balance) {
        return BankAccount.builder()
                .id(1L)
                .institution(institution)
                .accountNumber(ACCOUNT_NUMBER)
                .ownerName("tester")
                .balance(balance)
                .build();
    }

    private static BankWallet bankWallet(Institution institution, String walletAddress) {
        return BankWallet.builder()
                .id(1L)
                .institution(institution)
                .walletAddress(walletAddress)
                .encryptedPrivateKey("encrypted")
                .build();
    }

    private static TransactionReceipt receipt(String txHash, Long blockNumber) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(txHash);
        receipt.setBlockNumber("0x" + BigInteger.valueOf(blockNumber).toString(16));
        receipt.setStatus("0x1");
        return receipt;
    }

    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }
}
