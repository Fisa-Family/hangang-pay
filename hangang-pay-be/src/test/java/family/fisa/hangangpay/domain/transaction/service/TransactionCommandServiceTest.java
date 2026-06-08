package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.internal.payment.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelExecutionStateWriter;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentExecutionStateWriter;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    @Mock private CancelRequestHashGenerator cancelRequestHashGenerator;

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
                        cancelExecutionStateWriter,
                        cancelRequestHashGenerator);

        // 취소 멱등 해시 생성기는 비-null 해시를 반환해야 beginCancel(anyString, anyString) 매칭이 성립한다.
        lenient()
                .when(cancelRequestHashGenerator.generate(anyString(), anyLong()))
                .thenReturn(REQUEST_HASH);
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

        // 타임아웃은 재시도 대상 → bank 호출 2회(원본+재시도) 후에도 미해결이면 UNKNOWN
        verify(bankClient, times(2)).payment(prepared.toBankPaymentRequest());
        verify(paymentExecutionStateWriter).markUnknown(TRANSACTION_UUID);
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, unknownResponse);
        verify(paymentExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Bank가 422(ALREADY_FAILED)면 재시도 없이 FAILED로 확정하고 예외를 던진다")
    void executePayment_terminalFailure_throwsAndCompletesFailed() {
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));

        given(
                        paymentExecutionStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));
        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(bankError(422, "Unprocessable Entity", "TRANSACTION_ALREADY_FAILED"));
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        assertThatThrownBy(
                        () ->
                                transactionCommandService.executePayment(
                                        USER_ID,
                                        USER_PARTY_ID,
                                        TRANSACTION_UUID,
                                        new PaymentExecuteRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_ALREADY_FAILED);

        // 종단 실패 → 재시도 없음(1회), FAILED 확정, snapshot 미적재
        verify(bankClient).payment(prepared.toBankPaymentRequest());
        verify(paymentExecutionStateWriter).completeFailed(TRANSACTION_UUID);
        verify(paymentExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
        verify(paymentIdempotencyStore, never()).completeExecution(any(), any());
    }

    @Test
    @DisplayName("일시적 오류(timeout) 후 재시도에서 성공하면 SUCCESS로 확정된다")
    void executePayment_retrySucceeds() {
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));
        PaymentResponse bankResponse = successBankPaymentResponse("0x-tx");
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
        // 1차 timeout → 2차 성공
        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(new ResourceAccessException("timeout"))
                .willReturn(bankResponse);
        given(
                        paymentExecutionStateWriter.completeSuccess(
                                TRANSACTION_UUID,
                                bankResponse.txHash(),
                                String.valueOf(bankResponse.bankTransactionId()),
                                bankResponse.confirmedAt()))
                .willReturn(expected);
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        PaymentExecutionResponse response =
                transactionCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        assertThat(response).isSameAs(expected);
        verify(bankClient, times(2)).payment(prepared.toBankPaymentRequest());
        verify(paymentExecutionStateWriter)
                .completeSuccess(
                        TRANSACTION_UUID,
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.bankTransactionId()),
                        bankResponse.confirmedAt());
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, expected);
    }

    @Test
    @DisplayName("409(DUPLICATE_PROCESSING)는 재시도 후에도 미해결이면 UNKNOWN으로 끝난다")
    void executePayment_duplicateProcessing_retriesThenUnknown() {
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
        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(bankError(409, "Conflict", "TRANSACTION_DUPLICATE_PROCESSING"));
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

        assertThat(response).isSameAs(unknownResponse);
        // 409는 재시도 대상 → 2회 호출 후 UNKNOWN, FAILED 아님
        verify(bankClient, times(2)).payment(prepared.toBankPaymentRequest());
        verify(paymentExecutionStateWriter).markUnknown(TRANSACTION_UUID);
        verify(paymentExecutionStateWriter, never()).completeFailed(any());
    }

    @Test
    @DisplayName("UNKNOWN 복구 시 Bank SUCCESS 결과로 상태를 SUCCESS로 갱신한다")
    void recoverPayment_updatesStatusFromBankSuccess() {
        BankTransactionStatusResponse bankStatus = recoveryBankStatus(TransactionStatus.SUCCESS);
        PaymentExecutionResponse expected = paymentRecoveryResponse(TransactionStatus.SUCCESS);

        givenPaymentRecoveryBase(bankStatus, expected);

        PaymentExecutionResponse response =
                transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response).isSameAs(expected);

        InOrder inOrder = inOrder(paymentExecutionStateWriter, bankClient);
        inOrder.verify(paymentExecutionStateWriter)
                .prepareRecovery(USER_PARTY_ID, TRANSACTION_UUID);
        inOrder.verify(bankClient).getTransactionStatus(TRANSACTION_UUID);
        inOrder.verify(paymentExecutionStateWriter)
                .applyRecoveryResult(TRANSACTION_UUID, bankStatus);
    }

    @Test
    @DisplayName("UNKNOWN 복구 시 Bank FAILED 결과로 상태를 FAILED로 갱신한다")
    void recoverPayment_updatesStatusFromBankFailed() {
        BankTransactionStatusResponse bankStatus = recoveryBankStatus(TransactionStatus.FAILED);
        PaymentExecutionResponse expected = paymentRecoveryResponse(TransactionStatus.FAILED);

        givenPaymentRecoveryBase(bankStatus, expected);

        PaymentExecutionResponse response =
                transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response).isSameAs(expected);
        verify(paymentExecutionStateWriter).applyRecoveryResult(TRANSACTION_UUID, bankStatus);
    }

    @Test
    @DisplayName("Bank가 아직 PROCESSING이면 복구 가능한 상태로 남긴다")
    void recoverPayment_keepsRecoverableWhenBankStillProcessing() {
        BankTransactionStatusResponse bankStatus = recoveryBankStatus(TransactionStatus.PROCESSING);
        PaymentExecutionResponse expected = paymentRecoveryResponse(TransactionStatus.UNKNOWN);

        givenPaymentRecoveryBase(bankStatus, expected);

        PaymentExecutionResponse response =
                transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response).isSameAs(expected);
        verify(paymentExecutionStateWriter).applyRecoveryResult(TRANSACTION_UUID, bankStatus);
    }

    @Test
    @DisplayName("Bank SUCCESS 조회 결과에 txHash가 없으면 복구 결과 오류가 발생한다")
    void recoverPayment_bankSuccessWithoutTxHashThrowsInvalidRecoveryResult() {
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        TRANSACTION_UUID,
                        101L,
                        TransactionStatus.SUCCESS,
                        null,
                        LocalDateTime.of(2026, 5, 25, 10, 5));
        givenPaymentRecoveryThrows(
                bankStatus, TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverPayment(
                                        USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    @Test
    @DisplayName("Bank SUCCESS 조회 결과에 bankTransactionId가 없으면 복구 결과 오류가 발생한다")
    void recoverPayment_bankSuccessWithoutBankTransactionIdThrowsInvalidRecoveryResult() {
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        TRANSACTION_UUID,
                        null,
                        TransactionStatus.SUCCESS,
                        "0x-recovered",
                        LocalDateTime.of(2026, 5, 25, 10, 5));
        givenPaymentRecoveryThrows(
                bankStatus, TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverPayment(
                                        USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    @Test
    @DisplayName("SUCCESS 같은 최종 상태는 복구 대상이 아니다")
    void recoverPayment_rejectsNonRecoverableStatus() {
        givenPaymentRecoveryPrepareThrows(TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);

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
        givenPaymentRecoveryPrepareThrows(TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);

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
        BankTransactionStatusResponse bankStatus = recoveryBankStatus(TransactionStatus.SUCCESS);
        PaymentExecutionResponse expected = paymentRecoveryResponse(TransactionStatus.SUCCESS);
        givenPaymentRecoveryBase(bankStatus, expected);

        transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        verify(paymentLockManager).withTransactionLock(eq(TRANSACTION_UUID), any());
    }

    @Test
    @DisplayName("복구해도 은행이 아직 PROCESSING이면 시도 횟수만 올린다(cap 진행)")
    void recoverPayment_stillProcessing_incrementsAttempt() {
        BankTransactionStatusResponse bankStatus = recoveryBankStatus(TransactionStatus.PROCESSING);
        PaymentExecutionResponse stillProcessing =
                paymentRecoveryResponse(TransactionStatus.PROCESSING);
        givenPaymentRecoveryBase(bankStatus, stillProcessing);

        transactionCommandService.recoverPayment(USER_PARTY_ID, TRANSACTION_UUID);

        verify(paymentExecutionStateWriter).incrementRecoveryAttempt(TRANSACTION_UUID);
        verify(paymentIdempotencyStore, never()).completeExecution(anyString(), any());
        verify(paymentIdempotencyStore, never()).failExecution(anyString());
    }

    private void givenPaymentRecoveryBase(
            BankTransactionStatusResponse bankStatus, PaymentExecutionResponse response) {
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(
                        invocation -> {
                            Supplier<?> supplier = invocation.getArgument(1);
                            return supplier.get();
                        });
        given(paymentExecutionStateWriter.prepareRecovery(USER_PARTY_ID, TRANSACTION_UUID))
                .willReturn(TRANSACTION_UUID);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID)).willReturn(bankStatus);
        given(paymentExecutionStateWriter.applyRecoveryResult(TRANSACTION_UUID, bankStatus))
                .willReturn(response);
    }

    private void givenPaymentRecoveryThrows(
            BankTransactionStatusResponse bankStatus, TransactionErrorCode errorCode) {
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(paymentExecutionStateWriter.prepareRecovery(USER_PARTY_ID, TRANSACTION_UUID))
                .willReturn(TRANSACTION_UUID);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID)).willReturn(bankStatus);
        given(paymentExecutionStateWriter.applyRecoveryResult(TRANSACTION_UUID, bankStatus))
                .willThrow(new BusinessException(errorCode));
    }

    private void givenPaymentRecoveryPrepareThrows(TransactionErrorCode errorCode) {
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(paymentExecutionStateWriter.prepareRecovery(USER_PARTY_ID, TRANSACTION_UUID))
                .willThrow(new BusinessException(errorCode));
    }

    private BankTransactionStatusResponse recoveryBankStatus(TransactionStatus status) {
        return new BankTransactionStatusResponse(
                TRANSACTION_UUID,
                status == TransactionStatus.SUCCESS ? 101L : null,
                status,
                status == TransactionStatus.SUCCESS ? "0x-recovered" : null,
                LocalDateTime.of(2026, 5, 25, 10, 5));
    }

    private PaymentExecutionResponse paymentRecoveryResponse(TransactionStatus status) {
        return new PaymentExecutionResponse(
                TRANSACTION_UUID,
                status,
                "APV-2026-00000123",
                status == TransactionStatus.SUCCESS ? "0x-recovered" : null,
                new BigDecimal("10000"),
                "성수 한강카페",
                LocalDateTime.of(2026, 5, 25, 10, 5));
    }

    // ===== 결제 취소 =====

    @Test
    @DisplayName("정상 취소 시 Bank를 호출하고 SUCCESS로 확정된다")
    void executeCancel_success() {
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
                transactionCommandService.executeCancel(
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
    void executeCancel_failsWhenMerchantIsNotReceiver() {
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
                                transactionCommandService.executeCancel(
                                        OTHER_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("원본 결제가 SUCCESS 상태가 아니면 취소가 거부된다")
    void executeCancel_failsWhenPaymentNotSuccess() {
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
                                transactionCommandService.executeCancel(
                                        MERCHANT_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_CANCELLABLE);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("동일 원본에 SUCCESS CANCEL이 이미 존재하면 재취소가 거부된다")
    void executeCancel_failsWhenAlreadyCancelled() {
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
                                transactionCommandService.executeCancel(
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
    void executeCancel_marksUnknownWhenBankTimeout() {
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
                transactionCommandService.executeCancel(
                        MERCHANT_PARTY_ID, TRANSACTION_ID, new PaymentCancelRequest("123456"));

        // 4. UNKNOWN 응답 검증
        assertThat(response).isSameAs(unknownResponse);
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(response.txHash()).isNull();

        // 5. 호출 흐름 검증 — completeSuccess는 절대 호출되면 안 된다
        verify(cancelExecutionStateWriter)
                .prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456");
        // 타임아웃은 재시도 대상 → bank 호출 2회 후에도 미해결이면 UNKNOWN
        verify(bankClient, times(2)).cancel(prepared.toBankCancelRequest());
        verify(cancelExecutionStateWriter).markUnknown(CANCEL_UUID);
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, unknownResponse);
        verify(cancelExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("취소 Bank가 422(ALREADY_FAILED)면 재시도 없이 FAILED로 확정하고 예외를 던진다")
    void executeCancel_terminalFailure_throwsAndCompletesFailed() {
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelExecutionStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        given(bankClient.cancel(prepared.toBankCancelRequest()))
                .willThrow(bankError(422, "Unprocessable Entity", "TRANSACTION_ALREADY_FAILED"));

        assertThatThrownBy(
                        () ->
                                transactionCommandService.executeCancel(
                                        MERCHANT_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.CANCEL_ALREADY_FAILED);

        verify(bankClient).cancel(prepared.toBankCancelRequest()); // 재시도 없음(종단)
        verify(cancelExecutionStateWriter).completeFailed(CANCEL_UUID);
        verify(cancelExecutionStateWriter, never()).completeSuccess(any(), any(), any(), any());
        verify(cancelIdempotencyStore, never()).completeCancel(any(), any());
    }

    // ===== recover =====

    @Test
    @DisplayName("UNKNOWN CANCEL이 Bank SUCCESS이면 SUCCESS로 복구된다")
    void recoverCancel_successFromUnknown() {
        CancelExecutionPrepared prepared = cancelRecoveryPrepared();
        BankTransactionStatusResponse bankStatus =
                cancelRecoveryBankStatus(TransactionStatus.SUCCESS);
        PaymentCancelResponse expected = cancelRecoveryResponse(TransactionStatus.SUCCESS);

        givenCancelRecoveryBase(prepared, bankStatus, expected);

        PaymentCancelResponse response =
                transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        assertThat(response).isSameAs(expected);

        InOrder inOrder = inOrder(cancelExecutionStateWriter, bankClient);
        inOrder.verify(cancelExecutionStateWriter)
                .prepareRecovery(MERCHANT_PARTY_ID, TRANSACTION_ID);
        inOrder.verify(bankClient).getTransactionStatus(CANCEL_UUID);
        inOrder.verify(cancelExecutionStateWriter).applyRecoveryResult(CANCEL_UUID, bankStatus);
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, expected);
    }

    @Test
    @DisplayName("UNKNOWN CANCEL이 Bank FAILED이면 FAILED로 확정된다")
    void recoverCancel_failedFromUnknown() {
        CancelExecutionPrepared prepared = cancelRecoveryPrepared();
        BankTransactionStatusResponse bankStatus =
                cancelRecoveryBankStatus(TransactionStatus.FAILED);
        PaymentCancelResponse expected = cancelRecoveryResponse(TransactionStatus.FAILED);

        givenCancelRecoveryBase(prepared, bankStatus, expected);

        PaymentCancelResponse response =
                transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        assertThat(response).isSameAs(expected);
        verify(cancelIdempotencyStore, never()).completeCancel(any(), any());
    }

    @Test
    @DisplayName("Bank가 아직 PROCESSING이면 CANCEL 상태를 UNKNOWN으로 유지한다")
    void recoverCancel_keepsUnknownWhenBankStillProcessing() {
        CancelExecutionPrepared prepared = cancelRecoveryPrepared();
        BankTransactionStatusResponse bankStatus =
                cancelRecoveryBankStatus(TransactionStatus.PROCESSING);
        PaymentCancelResponse expected = cancelRecoveryResponse(TransactionStatus.UNKNOWN);

        givenCancelRecoveryBase(prepared, bankStatus, expected);

        PaymentCancelResponse response =
                transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        assertThat(response).isSameAs(expected);
        verify(cancelIdempotencyStore, never()).completeCancel(any(), any());
    }

    @Test
    @DisplayName("복구 가능한 CANCEL이 없으면 예외가 발생하고 Bank는 호출되지 않는다")
    void recoverCancel_failsWhenNoRecoverableCancel() {
        givenCancelRecoveryPrepareThrows(TransactionErrorCode.CANCEL_NOT_RECOVERABLE);

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
        givenCancelRecoveryPrepareThrows(TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);

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
    void executeCancel_bankServerError_marksUnknownAndStoresSnapshot() {
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
                transactionCommandService.executeCancel(
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
        CancelExecutionPrepared prepared = cancelRecoveryPrepared();
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        CANCEL_UUID,
                        888L,
                        TransactionStatus.SUCCESS,
                        null, // txHash 없음
                        LocalDateTime.of(2026, 5, 27, 14, 30));

        givenCancelRecoveryThrows(
                prepared, bankStatus, TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        assertThatThrownBy(
                        () ->
                                transactionCommandService.recoverCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    private void givenCancelRecoveryBase(
            CancelExecutionPrepared prepared,
            BankTransactionStatusResponse bankStatus,
            PaymentCancelResponse response) {
        givenCancelRecoveryLock();
        given(cancelExecutionStateWriter.prepareRecovery(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .willReturn(prepared);
        given(bankClient.getTransactionStatus(prepared.cancelTransactionUuid()))
                .willReturn(bankStatus);
        given(
                        cancelExecutionStateWriter.applyRecoveryResult(
                                prepared.cancelTransactionUuid(), bankStatus))
                .willReturn(response);
    }

    private void givenCancelRecoveryThrows(
            CancelExecutionPrepared prepared,
            BankTransactionStatusResponse bankStatus,
            TransactionErrorCode errorCode) {
        givenCancelRecoveryLock();
        given(cancelExecutionStateWriter.prepareRecovery(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .willReturn(prepared);
        given(bankClient.getTransactionStatus(prepared.cancelTransactionUuid()))
                .willReturn(bankStatus);
        given(
                        cancelExecutionStateWriter.applyRecoveryResult(
                                prepared.cancelTransactionUuid(), bankStatus))
                .willThrow(new BusinessException(errorCode));
    }

    private void givenCancelRecoveryPrepareThrows(TransactionErrorCode errorCode) {
        givenCancelRecoveryLock();
        given(cancelExecutionStateWriter.prepareRecovery(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .willThrow(new BusinessException(errorCode));
    }

    private void givenCancelRecoveryLock() {
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    private CancelExecutionPrepared cancelRecoveryPrepared() {
        return new CancelExecutionPrepared(
                CANCEL_UUID, TRANSACTION_UUID, "0x-merchant", "0x-user", new BigDecimal("10000"));
    }

    private BankTransactionStatusResponse cancelRecoveryBankStatus(TransactionStatus status) {
        return new BankTransactionStatusResponse(
                CANCEL_UUID,
                status == TransactionStatus.SUCCESS ? 888L : null,
                status,
                status == TransactionStatus.SUCCESS ? "0x-recovered-cancel" : null,
                LocalDateTime.of(2026, 5, 27, 14, 30));
    }

    private PaymentCancelResponse cancelRecoveryResponse(TransactionStatus status) {
        return new PaymentCancelResponse(
                CANCEL_UUID,
                status,
                status == TransactionStatus.SUCCESS ? "APV-2026-00000456" : null,
                status == TransactionStatus.SUCCESS ? "0x-recovered-cancel" : null,
                new BigDecimal("10000"),
                LocalDateTime.of(2026, 5, 27, 14, 30));
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

    /** bank 에러 응답(JSON body에 code 포함)을 던지는 RestClientResponseException 생성 */
    private RestClientResponseException bankError(int status, String statusText, String bankCode) {
        byte[] body = ("{\"code\":\"" + bankCode + "\"}").getBytes(StandardCharsets.UTF_8);
        return new RestClientResponseException(statusText, status, statusText, null, body, null);
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
