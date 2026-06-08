package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelExecutionStateWriter;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentExecutionStateWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnknownPaymentRecoverySchedulerTest {

    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long USER_PARTY_ID = 10L;
    private static final Long PAYMENT_ID = 100L;
    private static final Long CANCEL_ID = 200L;
    private static final String PAYMENT_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String CANCEL_UUID = "22222222-2222-2222-2222-222222222222";

    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionCommandService transactionCommandService;
    @Mock private PaymentExecutionStateWriter paymentExecutionStateWriter;
    @Mock private CancelExecutionStateWriter cancelExecutionStateWriter;

    @InjectMocks private UnknownPaymentRecoveryScheduler scheduler;

    @Test
    @DisplayName("UNKNOWN CANCEL마다 원본 PAYMENT를 조회해 recoverCancel을 호출한다")
    void resolveUnknownCancels_callsRecoverCancelForEachUnknown() {
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN, 0);
        Transaction originalPayment = paymentTransaction();

        given(transactionRepository.findAllUnknownByType(TransactionType.CANCEL))
                .willReturn(List.of(cancelTx));
        given(transactionRepository.findByTransactionUuid(PAYMENT_UUID))
                .willReturn(Optional.of(originalPayment));

        scheduler.resolveUnknownCancels();

        verify(transactionCommandService).recoverCancel(MERCHANT_PARTY_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("한 건 복구가 실패해도 나머지 건은 계속 처리된다")
    void resolveUnknownCancels_continuesAfterSingleFailure() {
        Transaction firstCancel = cancelTransaction(TransactionStatus.UNKNOWN, 0);
        Transaction secondCancel = cancelTransaction(TransactionStatus.UNKNOWN, 0);
        Transaction originalPayment = paymentTransaction();

        given(transactionRepository.findAllUnknownByType(TransactionType.CANCEL))
                .willReturn(List.of(firstCancel, secondCancel));
        given(transactionRepository.findByTransactionUuid(PAYMENT_UUID))
                .willReturn(Optional.of(originalPayment));
        given(transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, PAYMENT_ID))
                .willThrow(new RuntimeException("bank timeout"))
                .willReturn(null);

        scheduler.resolveUnknownCancels();

        verify(transactionCommandService, times(2)).recoverCancel(MERCHANT_PARTY_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("UNKNOWN/오래된 PROCESSING CANCEL이 없으면 recoverCancel을 호출하지 않는다")
    void resolveUnknownCancels_skipsWhenNoTargets() {
        given(transactionRepository.findAllUnknownByType(TransactionType.CANCEL))
                .willReturn(List.of());

        scheduler.resolveUnknownCancels();

        verify(transactionCommandService, never()).recoverCancel(any(), any());
    }

    @Test
    @DisplayName("결제: 오래된 PROCESSING은 복구하고, 포기 대상은 EXPIRED로 닫는다")
    void resolveUnknownPayments_recoversStaleAndExpiresAbandoned() {
        given(transactionRepository.findAllUnknownByType(TransactionType.PAYMENT))
                .willReturn(List.of());
        given(
                        transactionRepository.findStaleProcessingByType(
                                eq(TransactionType.PAYMENT), any(), anyInt()))
                .willReturn(List.of(staleProcessingPayment()));
        given(
                        transactionRepository.findAbandonedProcessingByType(
                                eq(TransactionType.PAYMENT), any(), anyInt()))
                .willReturn(List.of(abandonedProcessingPayment()));

        scheduler.resolveUnknownPayments();

        // 오래된 PROCESSING은 복구 시도
        verify(transactionCommandService).recoverPayment(USER_PARTY_ID, PAYMENT_UUID);
        // 포기 대상은 EXPIRED 터미널로 닫아 다음 주기 sweep·재알림에서 제외한다
        verify(paymentExecutionStateWriter).markExpired(PAYMENT_UUID);
    }

    @Test
    @DisplayName("취소: 포기 대상은 EXPIRED로 닫는다")
    void resolveUnknownCancels_expiresAbandoned() {
        given(transactionRepository.findAllUnknownByType(TransactionType.CANCEL))
                .willReturn(List.of());
        given(
                        transactionRepository.findStaleProcessingByType(
                                eq(TransactionType.CANCEL), any(), anyInt()))
                .willReturn(List.of());
        given(
                        transactionRepository.findAbandonedProcessingByType(
                                eq(TransactionType.CANCEL), any(), anyInt()))
                .willReturn(List.of(cancelTransaction(TransactionStatus.PROCESSING, 10)));

        scheduler.resolveUnknownCancels();

        verify(cancelExecutionStateWriter).markExpired(CANCEL_UUID);
    }

    // ===== 픽스처 =====

    private Transaction cancelTransaction(TransactionStatus status, int attemptCount) {
        // CANCEL의 fromParty는 가맹점 — createCancel()이 원본 PAYMENT 방향을 뒤집기 때문
        Party merchantParty =
                Party.builder().id(MERCHANT_PARTY_ID).partyType(PartyType.MERCHANT).build();
        Party userParty = Party.builder().id(USER_PARTY_ID).partyType(PartyType.USER).build();

        return Transaction.builder()
                .id(CANCEL_ID)
                .transactionUuid(CANCEL_UUID)
                .originalTransactionUuid(PAYMENT_UUID)
                .transactionType(TransactionType.CANCEL)
                .status(status)
                .fromParty(merchantParty)
                .toParty(userParty)
                .amount(new BigDecimal("10000"))
                .reconcileAttemptCount(attemptCount)
                .build();
    }

    private Transaction paymentTransaction() {
        return Transaction.builder()
                .id(PAYMENT_ID)
                .transactionUuid(PAYMENT_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("10000"))
                .build();
    }

    private Transaction staleProcessingPayment() {
        Party userParty = Party.builder().id(USER_PARTY_ID).partyType(PartyType.USER).build();
        return Transaction.builder()
                .id(PAYMENT_ID)
                .transactionUuid(PAYMENT_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(TransactionStatus.PROCESSING)
                .fromParty(userParty)
                .amount(new BigDecimal("10000"))
                .build();
    }

    private Transaction abandonedProcessingPayment() {
        Party userParty = Party.builder().id(USER_PARTY_ID).partyType(PartyType.USER).build();
        return Transaction.builder()
                .id(PAYMENT_ID)
                .transactionUuid(PAYMENT_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(TransactionStatus.PROCESSING)
                .fromParty(userParty)
                .amount(new BigDecimal("10000"))
                .reconcileAttemptCount(10)
                .build();
    }
}
