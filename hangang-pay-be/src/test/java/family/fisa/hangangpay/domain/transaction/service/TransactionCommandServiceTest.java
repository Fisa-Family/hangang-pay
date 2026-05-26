package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.dto.PaymentResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class TransactionCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_HASH = "server-generated-request-hash";

    @Mock private TransactionRepository transactionRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private PartyRepository partyRepository;
    @Mock private BankClient bankClient;

    @Mock private PaymentIdempotencyStore paymentIdempotencyStore;
    @Mock private PaymentLockManager paymentLockManager;
    @Mock private PaymentRateLimiter paymentRateLimiter;
    @Mock private PaymentExecutionStateWriter paymentExecutionStateWriter;

    private TransactionCommandService transactionCommandService;

    @BeforeEach
    void setUP() {
        transactionCommandService =
                new TransactionCommandService(
                        transactionRepository,
                        merchantRepository,
                        walletRepository,
                        userRepository,
                        partyRepository,
                        bankClient,
                        paymentIdempotencyStore,
                        paymentLockManager,
                        paymentRateLimiter,
                        paymentExecutionStateWriter);
    }

    @Test
    @DisplayName("결제 의도 생성 시 PENDING PAYMENT 거래가 저장된다")
    void createPaymentIntent_savesPendingPaymentTransaction() {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);
        Merchant merchant = merchant(merchantParty);
        Wallet userWallet = wallet(1L, userParty, "0x-user");
        Wallet merchantWallet = wallet(2L, merchantParty, "0x-merchant");

        PaymentIntentCreateRequest request =
                new PaymentIntentCreateRequest(MERCHANT_PARTY_ID, new BigDecimal("10000"), "아메리카노");

        given(partyRepository.findById(USER_PARTY_ID)).willReturn(Optional.of(userParty));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant));
        given(walletRepository.findByParty_Id(USER_PARTY_ID)).willReturn(Optional.of(userWallet));
        given(walletRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchantWallet));
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentIntentResponse response =
                transactionCommandService.createPaymentIntent(USER_PARTY_ID, request);

        assertThat(response.transactionUuid()).isNotBlank();
        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.merchantPartyId()).isEqualTo(MERCHANT_PARTY_ID);
        assertThat(response.amount()).isEqualByComparingTo("10000");
        assertThat(response.itemName()).isEqualTo("아메리카노");

        verify(paymentRateLimiter).checkIntentRateLimit(USER_PARTY_ID, MERCHANT_PARTY_ID);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction saved = captor.getValue();
        assertThat(saved.getTransactionUuid()).isNotBlank();
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(saved.getFromParty()).isSameAs(userParty);
        assertThat(saved.getToParty()).isSameAs(merchantParty);
        assertThat(saved.getFromWallet()).isSameAs(userWallet);
        assertThat(saved.getToWallet()).isSameAs(merchantWallet);
        assertThat(saved.getAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getItemName()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("정상 실행 시 PENDING -> PROCESSING -> SUCCESS 상태로 끝난다")
    void executePayment_success() {
        // given
        // REQUIRES_NEW 구간에서 결제 실행 검증과 PROCESSING 전환이 끝났다고 가정
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));

        // Bank 서버 결제 결과 정상 반환
        PaymentResponse bankResponse = successBankPaymentResponse("0x-tx");

        // SUCCESS 저장 후 command service가 최종 반환할 응답
        PaymentExecutionResponse expected =
                new PaymentExecutionResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.SUCCESS,
                        "APV-2026-00000123",
                        "0x-tx",
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        LocalDateTime.of(2026, 5, 25, 10, 0));

        given(
                        paymentExecutionStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));

        given(
                        paymentExecutionStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));

        given(bankClient.payment(prepared.toBankPaymentRequest())).willReturn(bankResponse);

        given(
                        paymentExecutionStateWriter.completeSuccess(
                                TRANSACTION_UUID,
                                bankResponse.txHash(),
                                String.valueOf(bankResponse.bankTransactionId()),
                                bankResponse.confirmedAt()))
                .willReturn(expected);

        // Redis lock mock은 락 획득 성공 후 콜백을 바로 실행하도록 만든다.
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(
                        invocation -> {
                            Supplier<?> supplier = invocation.getArgument(1);
                            return supplier.get();
                        });

        // when
        PaymentExecutionResponse response =
                transactionCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        // then
        assertThat(response).isSameAs(expected);

        verify(paymentExecutionStateWriter)
                .prepareExecution(USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456");

        verify(bankClient).payment(prepared.toBankPaymentRequest());

        verify(paymentExecutionStateWriter)
                .completeSuccess(
                        TRANSACTION_UUID,
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.bankTransactionId()),
                        bankResponse.confirmedAt());

        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, expected);
    }

    @Test
    @DisplayName("Bank 타임아웃 시 PENDING -> PROCESSING -> UNKNOWN 상태로 끝난다")
    void executePayment_timeoutMarksUnknown() {
        // given
        // REQUIRES_NEW 구간에서 검증과 PROCESSING 전환이 끝났다고 가정한다.
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));

        PaymentExecutionResponse unknownResponse =
                new PaymentExecutionResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.UNKNOWN,
                        "APV-2026-00000123",
                        null,
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        LocalDateTime.of(2026, 5, 25, 10, 0));

        given(
                        paymentExecutionStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));

        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(new ResourceAccessException("timeout"));

        given(paymentExecutionStateWriter.markUnknown(TRANSACTION_UUID))
                .willReturn(unknownResponse);

        // Redis lock mock은 락 획득 성공 후 콜백을 바로 실행하도록 만든다.
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(
                        invocation -> {
                            Supplier<?> supplier = invocation.getArgument(1);
                            return supplier.get();
                        });

        // when
        PaymentExecutionResponse response =
                transactionCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        // then
        assertThat(response).isSameAs(unknownResponse);

        verify(paymentExecutionStateWriter)
                .prepareExecution(USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456");

        verify(bankClient).payment(prepared.toBankPaymentRequest());
        verify(paymentExecutionStateWriter).markUnknown(TRANSACTION_UUID);
        verify(paymentIdempotencyStore)
                .markExecutionStatus(TRANSACTION_UUID, TransactionStatus.UNKNOWN);
        verify(paymentExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("UNKNOWN 복구 시 Bank SUCCESS 결과로 상태를 SUCCESS로 갱신한다")
    void recoverPayment_updatesStatusFromBankSuccess() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);

        givenRecoveryBase(transaction);
        givenRecoveryMerchant(transaction);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID))
                .willReturn(
                        new BankTransactionStatusResponse(
                                TRANSACTION_UUID,
                                101L,
                                TransactionStatus.SUCCESS,
                                "0x-recovered",
                                LocalDateTime.of(2026, 5, 25, 10, 5)));

        PaymentExecutionResponse response =
                transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.txHash()).isEqualTo("0x-recovered");
        assertThat(response.merchantName()).isEqualTo("성수 한강카페");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getTxHash()).isEqualTo("0x-recovered");

        verify(paymentRateLimiter).checkRecoveryRateLimit(USER_PARTY_ID, TRANSACTION_UUID);
        verify(paymentRateLimiter).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("UNKNOWN 복구 시 Bank FAILED 결과로 상태를 FAILED로 갱신한다")
    void recoverPayment_updatesStatusFromBankFailed() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);

        givenRecoveryBase(transaction);
        givenRecoveryMerchant(transaction);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID))
                .willReturn(
                        new BankTransactionStatusResponse(
                                TRANSACTION_UUID,
                                null,
                                TransactionStatus.FAILED,
                                null,
                                LocalDateTime.of(2026, 5, 25, 10, 5)));

        PaymentExecutionResponse response =
                transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.merchantName()).isEqualTo("성수 한강카페");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getTxHash()).isNull();

        verify(paymentRateLimiter).checkRecoveryRateLimit(USER_PARTY_ID, TRANSACTION_UUID);
        verify(paymentRateLimiter).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("Bank가 아직 PROCESSING이면 복구 가능한 상태로 남긴다")
    void recoverPayment_keepsRecoverableWhenBankStillProcessing() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);

        givenRecoveryBase(transaction);
        givenRecoveryMerchant(transaction);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID))
                .willReturn(
                        new BankTransactionStatusResponse(
                                TRANSACTION_UUID,
                                null,
                                TransactionStatus.PROCESSING,
                                null,
                                LocalDateTime.of(2026, 5, 25, 10, 5)));

        PaymentExecutionResponse response =
                transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(response.merchantName()).isEqualTo("성수 한강카페");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(transaction.getTxHash()).isNull();

        verify(paymentRateLimiter).checkRecoveryRateLimit(USER_PARTY_ID, TRANSACTION_UUID);
        verify(paymentRateLimiter).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("Bank SUCCESS 조회 결과에 txHash가 없으면 복구 결과 오류가 발생한다")
    void recoverPayment_bankSuccessWithoutTxHashThrowsInvalidRecoveryResult() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);

        givenRecoveryBase(transaction);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID))
                .willReturn(
                        new BankTransactionStatusResponse(
                                TRANSACTION_UUID,
                                101L,
                                TransactionStatus.SUCCESS,
                                null,
                                LocalDateTime.of(2026, 5, 25, 10, 5)));

        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverPayment(
                                        USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        verify(merchantRepository, never()).findByParty_Id(any());
    }

    @Test
    @DisplayName("Bank SUCCESS 조회 결과에 bankTransactionId가 없으면 복구 결과 오류가 발생한다")
    void recoverPayment_bankSuccessWithoutBankTransactionIdThrowsInvalidRecoveryResult() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);

        givenRecoveryBase(transaction);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID))
                .willReturn(
                        new BankTransactionStatusResponse(
                                TRANSACTION_UUID,
                                null,
                                TransactionStatus.SUCCESS,
                                "0x-recovered",
                                LocalDateTime.of(2026, 5, 25, 10, 5)));

        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverPayment(
                                        USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        verify(merchantRepository, never()).findByParty_Id(any());
    }

    @Test
    @DisplayName("SUCCESS 같은 최종 상태는 복구 대상이 아니다")
    void recoverPayment_rejectsNonRecoverableStatus() {
        Transaction transaction = paymentTransaction(TransactionStatus.SUCCESS);

        givenRecoveryBase(transaction);

        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverPayment(
                                        USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);

        verify(bankClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("로컬 PENDING 결제는 아직 Bank 실행 전이므로 복구 대상이 아니다")
    void recoverPayment_rejectsPendingStatus() {
        Transaction transaction = paymentTransaction(TransactionStatus.PENDING);

        givenRecoveryBase(transaction);

        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverPayment(
                                        USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);

        verify(bankClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("복구도 transactionUuid Redis lock 안에서 실행한다")
    void recoverPayment_usesTransactionLock() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);

        givenRecoveryBase(transaction);
        givenRecoveryMerchant(transaction);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID))
                .willReturn(
                        new BankTransactionStatusResponse(
                                TRANSACTION_UUID,
                                101L,
                                TransactionStatus.SUCCESS,
                                "0x-recovered",
                                LocalDateTime.of(2026, 5, 25, 10, 5)));

        transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        verify(paymentLockManager).withTransactionLock(eq(TRANSACTION_UUID), any());
    }

    private void givenRecoveryBase(Transaction transaction) {
        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(
                        invocation -> {
                            Supplier<?> supplier = invocation.getArgument(1);
                            return supplier.get();
                        });
    }

    private void givenRecoveryMerchant(Transaction transaction) {
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant(transaction.getToParty())));
    }

    private PaymentResponse successBankPaymentResponse(String txHash) {
        return new PaymentResponse(
                TRANSACTION_UUID,
                999L,
                txHash,
                100L,
                LocalDateTime.of(2026, 5, 25, 10, 0),
                new BigDecimal("90000"),
                new BigDecimal("110000"));
    }

    private Transaction paymentTransaction(TransactionStatus status) {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);

        return Transaction.builder()
                .id(123L)
                .transactionUuid(TRANSACTION_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(status)
                .fromParty(userParty)
                .toParty(merchantParty)
                .fromWallet(wallet(1L, userParty, "0x-user"))
                .toWallet(wallet(2L, merchantParty, "0x-merchant"))
                .amount(new BigDecimal("10000"))
                .approvalNumber("APV-2026-00000123")
                .itemName("아메리카노")
                .build();
    }

    private Merchant merchant(Party party) {
        return Merchant.builder()
                .id(1L)
                .party(party)
                .merchantName("성수 한강카페")
                .username("merchant")
                .passwordHash("password-hash")
                .businessNumber("123-45-67890")
                .ownerName("김한강")
                .build();
    }

    private Wallet wallet(Long id, Party party, String address) {
        return Wallet.builder().id(id).party(party).address(address).build();
    }

    private Party party(Long id, PartyType type) {
        return Party.builder().id(id).partyType(type).build();
    }
}
