package family.fisa.hangangpaybank.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequestResult;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus;
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
    @Mock private BlockchainSyncRequester syncRequester;

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
                        syncRequester);
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
    @DisplayName("결제 성공: DB 잔액 차감/증가, WalletLedger(SUCCESS), syncRequester 호출")
    void payment_success_updatesDbBalancesAndRequestsSync() {
        // given
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-1")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);
        given(
                        paymentStateWriter.saveSuccessWalletLedgers(
                                eq(from), eq(to), eq("uuid-1"), eq(AMOUNT)))
                .willReturn(LocalDateTime.now());
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        // when
        PaymentResponse response = service.payment(paymentRequest("uuid-1"));

        // then
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.confirmedAt()).isNotNull();
        // DB 잔액이 차감/증가됐는지 확인
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("400"));
        assertThat(to.getBalance()).isEqualByComparingTo(new BigDecimal("100"));
        verify(syncRequester).request(any(BlockchainSyncRequest.class));
    }

    @Test
    @DisplayName("결제 성공: contractCallService.pay() 미호출 확인 (DB-First)")
    void payment_success_doesNotCallContractPay() {
        // given
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-2")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);
        given(paymentStateWriter.saveSuccessWalletLedgers(any(), any(), any(), any()))
                .willReturn(LocalDateTime.now());
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        // when
        service.payment(paymentRequest("uuid-2"));

        // then - 동기 블록체인 pay() 호출이 없어야 한다
        verify(contractCallService, never()).pay(any(), any(), any());
    }

    // ── PAYMENT 실패 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("비가맹점 수신자: WalletLedger(FAILED) 저장, 잔액 미변경")
    void payment_nonMerchant_savesFailedLedgerAndKeepsBalance() {
        // given
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-3")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> service.payment(paymentRequest("uuid-3")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        // FAILED WalletLedger가 저장되어야 한다
        verify(paymentStateWriter)
                .saveFailedWalletLedgers(eq(from), eq(to), eq("uuid-3"), eq(AMOUNT));
        // 잔액은 변경되지 않아야 한다
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("500"));
        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("DB 잔액 부족: WalletLedger(FAILED) 저장, 잔액 미변경")
    void payment_insufficientBalance_savesFailedLedger() {
        // given
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("50")); // 100보다 적음
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-4")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> service.payment(paymentRequest("uuid-4")))
                .isInstanceOf(BusinessException.class);

        verify(paymentStateWriter)
                .saveFailedWalletLedgers(eq(from), eq(to), eq("uuid-4"), eq(AMOUNT));
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("50"));
    }

    // ── PAYMENT 멱등성 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUCCESS 멱등성 재요청: syncRequester 재호출 없이 기존 응답 반환")
    void payment_idempotentSuccess_returnsExistingResultWithoutSync() {
        // given
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("400"));
        BankWallet to = wallet(TO_ADDRESS, new BigDecimal("100"));
        BlockchainLedger existing = successLedger("uuid-5");

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-5")).willReturn(Optional.of(existing));

        // when
        PaymentResponse response = service.payment(paymentRequest("uuid-5"));

        // then - 블록체인 재요청 없이 기존 결과 반환
        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(syncRequester, never()).request(any());
        verify(contractCallService, never()).isMerchant(any());
    }

    // ── CANCEL ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("취소 성공: WalletLedger(SUCCESS) 저장, syncRequester 호출")
    void cancel_success_savesLedgerAndRequestsSync() {
        // given
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500")); // 가맹점
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO); // 사용자

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-6")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(FROM_ADDRESS)).willReturn(true);
        given(
                        paymentStateWriter.saveSuccessWalletLedgers(
                                eq(from), eq(to), eq("uuid-6"), eq(AMOUNT)))
                .willReturn(LocalDateTime.now());
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        // when
        CancelResponse response = service.cancel(cancelRequest("uuid-6", "orig-1"));

        // then
        assertThat(response.transactionUuid()).isEqualTo("uuid-6");
        assertThat(response.originalTransactionUuid()).isEqualTo("orig-1");
        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(syncRequester).request(any(BlockchainSyncRequest.class));
    }

    @Test
    @DisplayName("취소 비가맹점: WalletLedger(FAILED) 저장, 잔액 미변경")
    void cancel_nonMerchant_savesFailedLedger() {
        // given
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-7")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(FROM_ADDRESS)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> service.cancel(cancelRequest("uuid-7", "orig-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        verify(paymentStateWriter)
                .saveFailedWalletLedgers(eq(from), eq(to), eq("uuid-7"), eq(AMOUNT));
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("500"));
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

    private static BlockchainLedger successLedger(String uuid) {
        return BlockchainLedger.builder()
                .id(1L)
                .institution(Institution.builder().id(1L).build())
                .idempotentKey(uuid)
                .status(BlockchainTxStatus.SUCCESS)
                .confirmedAt(LocalDateTime.now().minusMinutes(1))
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
