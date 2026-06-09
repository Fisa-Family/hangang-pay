package family.fisa.hangangpaybank.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
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
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class TransactionCommandServiceTest {

    private static final String FROM_ADDRESS = "0x000000000000000000000000000000000000aaaa";
    private static final String TO_ADDRESS = "0x000000000000000000000000000000000000bbbb";
    private static final BigDecimal AMOUNT = new BigDecimal("100");
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private BlockchainLedgerRepository blockchainLedgerRepository;
    @Mock private AccountLedgerRepository accountLedgerRepository;
    @Mock private ContractCallService contractCallService;
    @Mock private PaymentStateWriter paymentStateWriter;
    @Mock private PaymentExecutionService paymentExecutionService;

    private TransactionCommandService service;

    @BeforeEach
    void setUp() {
        service =
                new TransactionCommandService(
                        bankAccountRepository,
                        bankWalletRepository,
                        institutionRepository,
                        blockchainLedgerRepository,
                        accountLedgerRepository,
                        contractCallService,
                        paymentStateWriter,
                        paymentExecutionService);
    }

    // ── CHARGE (동기, 기존 흐름 유지) ───────────────────────────────────────────

    @Test
    @DisplayName("충전 완료 응답의 지갑 잔액은 컨트랙트 balanceOf 기준으로 반환한다")
    void charge_returnsContractWalletBalance() {
        // given
        Institution institution = institution();
        BankAccount bankAccount = bankAccount(institution, new BigDecimal("100000"));
        BankWallet bankWallet = wallet(TO_ADDRESS, BigDecimal.ZERO);
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-charge",
                        1L,
                        "1002-123-456789",
                        TO_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        TransactionReceipt receipt = receipt("0x-charge", 10L);

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(bankAccountRepository.findByInstitution_IdAndAccountNumber(1L, "1002-123-456789"))
                .willReturn(Optional.of(bankAccount));
        given(bankWalletRepository.findByWalletAddress(TO_ADDRESS))
                .willReturn(Optional.of(bankWallet));
        given(accountLedgerRepository.save(any(AccountLedger.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(contractCallService.charge(eq(1L), eq(TO_ADDRESS), any())).willReturn(receipt);
        given(blockchainLedgerRepository.save(any(BlockchainLedger.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(contractCallService.getBalance(TO_ADDRESS))
                .willReturn(toTokenUnit(new BigDecimal("12345")));

        // when
        ChargeResponse response = service.charge(request);

        // then
        assertThat(response.walletBalance()).isEqualByComparingTo(new BigDecimal("12345"));
        assertThat(response.txHash()).isEqualTo("0x-charge");
    }

    // ── PAYMENT 성공 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("결제 성공: 실행 서비스 결과를 그대로 반환한다")
    void payment_success_returnsExecutionResult() {
        // given
        PaymentResponse executionResponse =
                PaymentResponse.from(
                        "uuid-1",
                        family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus.SUCCESS,
                        LocalDateTime.now(),
                        new BigDecimal("400"),
                        new BigDecimal("100"));
        given(paymentExecutionService.payment(paymentRequest("uuid-1")))
                .willReturn(executionResponse);

        // when
        PaymentResponse response = service.payment(paymentRequest("uuid-1"));

        // then
        assertThat(response).isEqualTo(executionResponse);
    }

    @Test
    @DisplayName("결제 비즈니스 실패: rollback 이후 주소 기반 FAILED ledger 저장")
    void payment_businessFailure_savesFailedLedgerByAddress() {
        // given
        BusinessException failure =
                new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        given(paymentExecutionService.payment(paymentRequest("uuid-2"))).willThrow(failure);

        // when & then
        assertThatThrownBy(() -> service.payment(paymentRequest("uuid-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        verify(paymentStateWriter)
                .saveFailedWalletLedgersByAddress(
                        eq(FROM_ADDRESS), eq(TO_ADDRESS), eq("uuid-2"), eq(AMOUNT));
    }

    // ── CANCEL ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("취소 성공: 실행 서비스 결과를 그대로 반환한다")
    void cancel_success_returnsExecutionResult() {
        // given
        CancelResponse executionResponse =
                CancelResponse.from(
                        "uuid-6",
                        "orig-1",
                        family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus.SUCCESS,
                        LocalDateTime.now(),
                        new BigDecimal("400"),
                        new BigDecimal("100"));
        given(paymentExecutionService.cancel(cancelRequest("uuid-6", "orig-1")))
                .willReturn(executionResponse);

        // when
        CancelResponse response = service.cancel(cancelRequest("uuid-6", "orig-1"));

        // then
        assertThat(response).isEqualTo(executionResponse);
    }

    @Test
    @DisplayName("취소 비즈니스 실패: rollback 이후 주소 기반 FAILED ledger 저장")
    void cancel_businessFailure_savesFailedLedgerByAddress() {
        // given
        BusinessException failure =
                new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        given(paymentExecutionService.cancel(cancelRequest("uuid-7", "orig-2"))).willThrow(failure);

        // when & then
        assertThatThrownBy(() -> service.cancel(cancelRequest("uuid-7", "orig-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        verify(paymentStateWriter)
                .saveFailedWalletLedgersByAddress(
                        eq(FROM_ADDRESS), eq(TO_ADDRESS), eq("uuid-7"), eq(AMOUNT));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private PaymentRequest paymentRequest(String uuid) {
        return new PaymentRequest(uuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }

    private CancelRequest cancelRequest(String uuid, String originalUuid) {
        return new CancelRequest(uuid, originalUuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }

    private static BankWallet wallet(String address, BigDecimal balance) {
        BankWallet wallet =
                BankWallet.builder()
                        .id(1L)
                        .institution(Institution.builder().id(1L).build())
                        .walletAddress(address)
                        .encryptedPrivateKey("encrypted")
                        .build();
        wallet.updateBalance(balance);
        return wallet;
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
                .accountNumber("1002-123-456789")
                .ownerName("tester")
                .balance(balance)
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
