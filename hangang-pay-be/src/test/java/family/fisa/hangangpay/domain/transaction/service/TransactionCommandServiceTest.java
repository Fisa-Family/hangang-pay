package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.dto.CancelResponse;
import family.fisa.hangangpay.client.bank.dto.PaymentResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelLockManager;
import family.fisa.hangangpay.domain.transaction.internal.payment.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class TransactionCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long OTHER_PARTY_ID = 30L;
    private static final Long TRANSACTION_ID = 123L;
    private static final Long CANCEL_TRANSACTION_ID = 456L;

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String CANCEL_UUID = "22222222-2222-2222-2222-222222222222";
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
    @Mock private CancelExecutionStateWriter cancelExecutionStateWriter;
    @Mock private CancelIdempotencyStore cancelIdempotencyStore;
    @Mock private CancelLockManager cancelLockManager;

    private TransactionCommandService transactionCommandService;

    @BeforeEach
    void setUP() {
        transactionCommandService =
                new TransactionCommandService(
                        transactionRepository,
                        merchantRepository,
                        walletRepository,
                        partyRepository,
                        bankClient,
                        paymentIdempotencyStore,
                        paymentLockManager,
                        paymentRateLimiter,
                        cancelIdempotencyStore,
                        cancelLockManager,
                        paymentExecutionStateWriter,
                        cancelExecutionStateWriter);
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
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, unknownResponse);
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

    // ===== 결제 취소 =====

    @Test
    @DisplayName("정상 취소 시 Bank를 호출하고 SUCCESS로 확정된다")
    void cancelPayment_success() {
        // 1. prepareCancel 반환값 (CancelExecutionPrepared — 미구현, RED 의도)
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        CancelResponse bankResponse = successBankCancelResponse();

        PaymentCancelResponse expected =
                new PaymentCancelResponse(
                        CANCEL_UUID,
                        TransactionStatus.SUCCESS, // 1. status 필드 추가 — 성공 확정임을 명시
                        "APV-2026-00000456",
                        bankResponse.txHash(),
                        new BigDecimal("10000"),
                        bankResponse.confirmedAt());

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelExecutionStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        given(bankClient.cancel(prepared.toBankCancelRequest())).willReturn(bankResponse);
        given(
                        cancelExecutionStateWriter.completeSuccess(
                                CANCEL_UUID,
                                bankResponse.txHash(),
                                String.valueOf(bankResponse.bankTransactionId()),
                                bankResponse.confirmedAt()))
                .willReturn(expected);

        PaymentCancelResponse response =
                transactionCommandService.cancelPayment(
                        MERCHANT_PARTY_ID, TRANSACTION_ID, new PaymentCancelRequest("123456"));

        assertThat(response).isSameAs(expected);
        verify(cancelExecutionStateWriter)
                .prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456");
        verify(bankClient).cancel(prepared.toBankCancelRequest());
        verify(cancelExecutionStateWriter)
                .completeSuccess(
                        CANCEL_UUID,
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.bankTransactionId()),
                        bankResponse.confirmedAt());
    }

    @Test
    @DisplayName("세션 가맹점이 결제 수신자(toParty)가 아니면 취소가 거부된다")
    void cancelPayment_failsWhenMerchantIsNotReceiver() {
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelExecutionStateWriter.prepareCancel(OTHER_PARTY_ID, TRANSACTION_ID, "123456"))
                .willThrow(new BusinessException(TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN));

        assertThatThrownBy(
                        () ->
                                transactionCommandService.cancelPayment(
                                        OTHER_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("원본 결제가 SUCCESS 상태가 아니면 취소가 거부된다")
    void cancelPayment_failsWhenPaymentNotSuccess() {
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelExecutionStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willThrow(new BusinessException(TransactionErrorCode.PAYMENT_NOT_CANCELLABLE));

        assertThatThrownBy(
                        () ->
                                transactionCommandService.cancelPayment(
                                        MERCHANT_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_CANCELLABLE);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("동일 원본에 SUCCESS CANCEL이 이미 존재하면 재취소가 거부된다")
    void cancelPayment_failsWhenAlreadyCancelled() {
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelExecutionStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willThrow(new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_CANCELLED));

        assertThatThrownBy(
                        () ->
                                transactionCommandService.cancelPayment(
                                        MERCHANT_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_ALREADY_CANCELLED);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("Bank timeout 시 CANCEL이 UNKNOWN으로 저장되고 실패 확정이 아님을 반환한다")
    void cancelPayment_marksUnknownWhenBankTimeout() {
        // 1. prepareCancel 정상 완료 — CANCEL 레코드가 PROCESSING으로 DB에 커밋된 상태
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        // 2. UNKNOWN 상태 응답 — txHash, confirmedAt 없음 (은행 확정 전)
        PaymentCancelResponse unknownResponse =
                new PaymentCancelResponse(
                        CANCEL_UUID,
                        TransactionStatus.UNKNOWN, // 실패 확정이 아니라 "모름"
                        null, // 승인번호 없음
                        null, // txHash 없음
                        new BigDecimal("10000"),
                        null); // confirmedAt 없음

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelExecutionStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        // 3. Bank 네트워크 오류 — 요청이 도달했는지 알 수 없다
        given(bankClient.cancel(prepared.toBankCancelRequest()))
                .willThrow(new ResourceAccessException("connection timed out"));
        given(cancelExecutionStateWriter.markUnknown(CANCEL_UUID)).willReturn(unknownResponse);

        PaymentCancelResponse response =
                transactionCommandService.cancelPayment(
                        MERCHANT_PARTY_ID, TRANSACTION_ID, new PaymentCancelRequest("123456"));

        // 4. UNKNOWN 응답 검증
        assertThat(response).isSameAs(unknownResponse);
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(response.txHash()).isNull();

        // 5. 호출 흐름 검증 — completeSuccess는 절대 호출되면 안 된다
        verify(cancelExecutionStateWriter)
                .prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456");
        verify(bankClient).cancel(prepared.toBankCancelRequest());
        verify(cancelExecutionStateWriter).markUnknown(CANCEL_UUID);
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, unknownResponse);
        verify(cancelExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    // ===== recover =====

    @Test
    @DisplayName("UNKNOWN CANCEL이 Bank SUCCESS이면 SUCCESS로 복구된다")
    void recoverCancel_successFromUnknown() {
        // 1. 원본 PAYMENT — toParty가 MERCHANT이므로 소유권 검증 통과
        Transaction originalPayment = paymentTransaction(TransactionStatus.SUCCESS);

        // 2. 복구 대상 CANCEL — UNKNOWN 상태 (Bank 응답을 받지 못한 상태)
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);

        // 3. Bank가 SUCCESS를 반환 — 취소는 실제로 완료됐다
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        CANCEL_UUID,
                        888L,
                        TransactionStatus.SUCCESS,
                        "0x-recovered-cancel",
                        LocalDateTime.of(2026, 5, 27, 14, 30));

        // 4. Mock 설정
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(originalPayment));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(
                        transactionRepository.findRecoverableCancelByOriginalTransactionUuid(
                                TRANSACTION_UUID))
                .willReturn(Optional.of(cancelTx));
        given(bankClient.getTransactionStatus(CANCEL_UUID)).willReturn(bankStatus);

        // 5. 실행
        PaymentCancelResponse response =
                transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        // 6. 응답 검증 — SUCCESS 확정 상태여야 한다
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.txHash()).isEqualTo("0x-recovered-cancel");
        assertThat(response.confirmedAt()).isEqualTo(bankStatus.confirmedAt());

        // 7. 엔티티 상태 전환 검증 — recoverSuccess가 호출됐는지 간접 확인
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(cancelTx.getTxHash()).isEqualTo("0x-recovered-cancel");
    }

    @Test
    @DisplayName("UNKNOWN CANCEL이 Bank FAILED이면 FAILED로 확정된다")
    void recoverCancel_failedFromUnknown() {
        // 1. 원본 PAYMENT와 복구 대상 CANCEL 설정
        Transaction originalPayment = paymentTransaction(TransactionStatus.SUCCESS);
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);

        // 2. Bank가 FAILED를 반환 — 취소가 실패했음을 은행이 확인
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        CANCEL_UUID,
                        null, // FAILED이면 bankTransactionId 없음
                        TransactionStatus.FAILED,
                        null, // txHash 없음
                        LocalDateTime.of(2026, 5, 27, 14, 30));

        // 3. Mock 설정
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(originalPayment));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(
                        transactionRepository.findRecoverableCancelByOriginalTransactionUuid(
                                TRANSACTION_UUID))
                .willReturn(Optional.of(cancelTx));
        given(bankClient.getTransactionStatus(CANCEL_UUID)).willReturn(bankStatus);

        // 4. 실행
        PaymentCancelResponse response =
                transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        // 5. FAILED 확정 검증 — txHash, confirmedAt 없음
        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.txHash()).isNull();
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("Bank가 아직 PROCESSING이면 CANCEL 상태를 UNKNOWN으로 유지한다")
    void recoverCancel_keepsUnknownWhenBankStillProcessing() {
        // 1. 원본 PAYMENT와 복구 대상 CANCEL 설정
        Transaction originalPayment = paymentTransaction(TransactionStatus.SUCCESS);
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);

        // 2. Bank도 아직 처리 중 — 확정할 근거 없음
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        CANCEL_UUID,
                        null,
                        TransactionStatus.PROCESSING,
                        null,
                        null); // confirmedAt 없음 - 아직 미확정

        // 3. Mock 설정
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(originalPayment));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(
                        transactionRepository.findRecoverableCancelByOriginalTransactionUuid(
                                TRANSACTION_UUID))
                .willReturn(Optional.of(cancelTx));
        given(bankClient.getTransactionStatus(CANCEL_UUID)).willReturn(bankStatus);

        // 4. 실행
        PaymentCancelResponse response =
                transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        // 5. 상태 유지 검증 — FAILED로 확정하지 않는다는 것이 핵심
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(cancelTx.getTxHash()).isNull();
    }

    @Test
    @DisplayName("복구 가능한 CANCEL이 없으면 예외가 발생하고 Bank는 호출되지 않는다")
    void recoverCancel_failsWhenNoRecoverableCancel() {
        // 1. 원본 PAYMENT는 존재하지만
        Transaction originalPayment = paymentTransaction(TransactionStatus.SUCCESS);
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(originalPayment));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        // 2. 복구 대상 CANCEL이 없음 (예: 이미 SUCCESS/FAILED로 확정됐거나 아예 취소 기록 없음)
        given(
                        transactionRepository.findRecoverableCancelByOriginalTransactionUuid(
                                TRANSACTION_UUID))
                .willReturn(Optional.empty());

        // 3. CANCEL_NOT_RECOVERABLE 예외 발생 기대
        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.CANCEL_NOT_RECOVERABLE);

        // 4. Bank 조회 없음 — 복구 대상 없으면 Bank 호출도 없어야 한다
        verify(bankClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("세션 가맹점이 원본 결제 수신자가 아니면 복구가 거부된다")
    void recoverCancel_failsWhenMerchantIsNotReceiver() {
        // 1. toParty가 OTHER_PARTY_ID인 원본 PAYMENT — 현재 세션 가맹점과 다름
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party otherMerchantParty = party(OTHER_PARTY_ID, PartyType.MERCHANT);
        Transaction originalPayment =
                Transaction.builder()
                        .id(TRANSACTION_ID)
                        .transactionUuid(TRANSACTION_UUID)
                        .transactionType(TransactionType.PAYMENT)
                        .status(TransactionStatus.SUCCESS)
                        .fromParty(userParty)
                        .toParty(otherMerchantParty) // 2. 다른 가맹점이 수신자
                        .fromWallet(wallet(1L, userParty, "0x-user"))
                        .toWallet(wallet(3L, otherMerchantParty, "0x-other"))
                        .amount(new BigDecimal("10000"))
                        .build();

        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(originalPayment));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        // 3. MERCHANT_PARTY_ID는 수신자가 아님 → PAYMENT_CANCEL_FORBIDDEN 예외
        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);

        // 4. 소유권 실패 시 이후 흐름 없음
        verify(bankClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("Bank 서버 오류(RestClientResponseException) 시 CANCEL이 UNKNOWN으로 저장되고 snapshot이 적재된다")
    void cancelPayment_bankServerError_marksUnknownAndStoresSnapshot() {
        // 1. prepareCancel 정상 완료
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        // 2. UNKNOWN 응답
        PaymentCancelResponse unknownResponse =
                new PaymentCancelResponse(
                        CANCEL_UUID,
                        TransactionStatus.UNKNOWN,
                        null,
                        null,
                        new BigDecimal("10000"),
                        null);

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelExecutionStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        // 3. Bank 5xx
        given(bankClient.cancel(prepared.toBankCancelRequest()))
                .willThrow(
                        new RestClientResponseException(
                                "500", 500, "Internal Server Error", null, null, null));
        given(cancelExecutionStateWriter.markUnknown(CANCEL_UUID)).willReturn(unknownResponse);

        PaymentCancelResponse response =
                transactionCommandService.cancelPayment(
                        MERCHANT_PARTY_ID, TRANSACTION_ID, new PaymentCancelRequest("123456"));

        // 4. UNKNOWN 응답 반환 검증
        assertThat(response).isSameAs(unknownResponse);
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);

        // 5. snapshot 적재 검증 — markCancelStatus가 아니라 completeCancel
        verify(cancelExecutionStateWriter).markUnknown(CANCEL_UUID);
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, unknownResponse);
        verify(cancelExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Bank 서버 오류(RestClientResponseException) 시 PAYMENT가 UNKNOWN으로 저장되고 snapshot이 적재된다")
    void executePayment_bankServerError_marksUnknownAndStoresSnapshot() {
        // 1. prepareExecution 정상 완료
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
                        null,
                        null,
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        null);

        given(
                        paymentExecutionStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));
        // 2. Bank 5xx
        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(
                        new RestClientResponseException(
                                "500", 500, "Internal Server Error", null, null, null));
        given(paymentExecutionStateWriter.markUnknown(TRANSACTION_UUID))
                .willReturn(unknownResponse);
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        PaymentExecutionResponse response =
                transactionCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        // 3. UNKNOWN 응답 반환 검증
        assertThat(response).isSameAs(unknownResponse);
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);

        // 4. snapshot 적재 검증 — markExecutionStatus가 아니라 completeExecution
        verify(paymentExecutionStateWriter).markUnknown(TRANSACTION_UUID);
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, unknownResponse);
        verify(paymentExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("취소 복구 시 Bank SUCCESS인데 txHash가 없으면 복구 결과 오류가 발생한다")
    void recoverCancel_bankSuccessWithNullTxHash_throwsRecoveryResultInvalid() {
        // 1. 원본 PAYMENT와 복구 대상 CANCEL
        Transaction originalPayment = paymentTransaction(TransactionStatus.SUCCESS);
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);

        // 2. Bank SUCCESS인데 txHash 누락
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        CANCEL_UUID,
                        888L,
                        TransactionStatus.SUCCESS,
                        null, // txHash 없음
                        LocalDateTime.of(2026, 5, 27, 14, 30));

        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(originalPayment));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(
                        transactionRepository.findRecoverableCancelByOriginalTransactionUuid(
                                TRANSACTION_UUID))
                .willReturn(Optional.of(cancelTx));
        given(bankClient.getTransactionStatus(CANCEL_UUID)).willReturn(bankStatus);

        // 3. PAYMENT_RECOVERY_RESULT_INVALID 예외 발생
        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    private CancelResponse successBankCancelResponse() {
        return new CancelResponse(
                CANCEL_UUID,
                TRANSACTION_UUID,
                888L,
                "0x-cancel-tx",
                200L,
                LocalDateTime.of(2026, 5, 27, 14, 0),
                new BigDecimal("110000"),
                new BigDecimal("90000"));
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

    private Transaction cancelTransaction(TransactionStatus status) {
        // 1. CANCEL 방향 - 원본 PAYMENT 역방향 (가맹점 -> 소비자)
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);
        Party userParty = party(USER_PARTY_ID, PartyType.USER);

        return Transaction.builder()
                .id(CANCEL_TRANSACTION_ID)
                .transactionUuid(CANCEL_UUID)
                .originalTransactionUuid(TRANSACTION_UUID) // 2. 원본 PAYMENT UUID 참조
                .transactionType(TransactionType.CANCEL)
                .status(status)
                .fromParty(merchantParty) // 3. CANCEL fromParty = 가맹점
                .toParty(userParty)
                .fromWallet(wallet(2L, merchantParty, "0x-merchant"))
                .toWallet(wallet(1L, userParty, "0x-user"))
                .amount(new BigDecimal("10000"))
                .build();
    }
}
