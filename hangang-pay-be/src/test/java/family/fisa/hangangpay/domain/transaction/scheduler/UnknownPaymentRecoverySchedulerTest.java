package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.mockito.ArgumentMatchers.any;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private UnknownPaymentRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new UnknownPaymentRecoveryScheduler(
                        transactionRepository, transactionCommandService);
    }

    @Test
    @DisplayName("UNKNOWN CANCEL마다 원본 PAYMENT를 조회해 recoverCancel을 호출한다")
    void resolveUnknownCancels_callsRecoverCancelForEachUnknown() {
        // 1. UNKNOWN 상태의 CANCEL 거래 - fromParty가 가맹점 (역방향)
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);

        // 2. cancelTx.originalTransactionUuid로 조회될 원본 PAYMENT
        Transaction originalPayment = paymentTransaction();

        // 3. Mock 설정
        given(transactionRepository.findAllUnknownByType(TransactionType.CANCEL))
                .willReturn(List.of(cancelTx));
        given(transactionRepository.findByTransactionUuid(PAYMENT_UUID))
                .willReturn(Optional.of(originalPayment));

        // 4. 스케줄러 실행
        scheduler.resolveUnknownCancels();

        // 5. recoverCancel이 가맹점 partyId와 원본 PAYMENT id로 호출됐는지 검증
        verify(transactionCommandService).recoverCancel(MERCHANT_PARTY_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("한 건 복구가 실패해도 나머지 건은 계속 처리된다")
    void resolveUnknownCancels_continuesAfterSingleFailure() {
        // 1. UNKNOWN CANCEL 2건 - 각각 다른 UUID를 가지나 originalTransactionUuid는 동일 픽스처
        Transaction firstCancel = cancelTransaction(TransactionStatus.UNKNOWN);
        Transaction secondCancel = cancelTransaction(TransactionStatus.UNKNOWN);

        Transaction originalPayment = paymentTransaction();

        // 2. 두 건 모두 원본 PAYMENT 조회 성공
        given(transactionRepository.findAllUnknownByType(TransactionType.CANCEL))
                .willReturn(List.of(firstCancel, secondCancel));
        given(transactionRepository.findByTransactionUuid(PAYMENT_UUID))
                .willReturn(Optional.of(originalPayment));

        // 3. 첫 번째 recoverCancel만 예외 발생 - 두 번째는 정상
        given(transactionCommandService.recoverCancel(MERCHANT_PARTY_ID, PAYMENT_ID))
                .willThrow(new RuntimeException("bank timeout"))
                .willReturn(null);

        // 4. 스케줄러 실행 - 예외가 외부로 전파되면 안 된다
        scheduler.resolveUnknownCancels();

        // 5. 두 건 모두 시도됐는지 검증
        verify(transactionCommandService, times(2)).recoverCancel(MERCHANT_PARTY_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("UNKNOWN CANCEL이 없으면 recoverCancel을 호출하지 않는다")
    void resolveUnknownCancels_skipsWhenNoTargets() {
        // 1. 복구 대상 없음
        given(transactionRepository.findAllUnknownByType(TransactionType.CANCEL))
                .willReturn(List.of());

        // 2. 스케줄러 실행
        scheduler.resolveUnknownCancels();

        // 3. 서비스 호출 없음 검증
        verify(transactionCommandService, never()).recoverCancel(any(), any());
    }

    // ===== 픽스처 =====

    private Transaction cancelTransaction(TransactionStatus status) {
        // 1. CANCEL의 fromParty는 가맹점 — createCancel()이 원본 PAYMENT 방향을 뒤집기 때문
        Party merchantParty =
                Party.builder().id(MERCHANT_PARTY_ID).partyType(PartyType.MERCHANT).build();
        Party userParty = Party.builder().id(USER_PARTY_ID).partyType(PartyType.USER).build();

        return Transaction.builder()
                .id(CANCEL_ID)
                .transactionUuid(CANCEL_UUID)
                .originalTransactionUuid(PAYMENT_UUID) // 2. 원본 PAYMENT UUID 참조
                .transactionType(TransactionType.CANCEL)
                .status(status)
                .fromParty(merchantParty)
                .toParty(userParty)
                .amount(new BigDecimal("10000"))
                .build();
    }

    private Transaction paymentTransaction() {
        return Transaction.builder()
                .id(PAYMENT_ID) // 3. id — recoverCancel 호출에 필요
                .transactionUuid(PAYMENT_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("10000"))
                .build();
    }
}
