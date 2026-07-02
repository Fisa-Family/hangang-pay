package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeLockManager;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeReconcileServiceV1Test {

    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "11111111-1111-1111-1111-111111111111";
    private static final Long BANK_TX_ID = 999L;

    @Mock BankClient bankClient;
    @Mock ChargeLockManager chargeLockManager;
    @Mock ChargeStateWriter chargeStateWriter;
    @Mock ChargeIdempotencyStore chargeIdempotencyStore;

    @InjectMocks ChargeReconcileServiceV1 chargeReconcileService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(chargeLockManager.withChargeLock(eq(UUID), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    @Test
    @DisplayName("bank SUCCESS -> applyReconcileResult + 멱등 completeExecution")
    void bank_success() {
        BankTransactionStatusResponse bankStatus = bankStatus(TransactionStatus.SUCCESS, BANK_TX_ID);
        given(bankClient.getChargeStatus(UUID)).willReturn(bankStatus);
        ChargeExecuteResponse resp = response();
        given(chargeStateWriter.applyReconcileResult(UUID, bankStatus)).willReturn(resp);

        chargeReconcileService.reconcile(charge(TransactionStatus.UNKNOWN));

        verify(chargeStateWriter).applyReconcileResult(UUID, bankStatus);
        verify(chargeIdempotencyStore).completeExecution(UUID, resp);
    }

    @Test
    @DisplayName("bank FAILED -> 멱등 상태 FAILED 마킹, completeExecution 없음")
    void bank_failed() {
        BankTransactionStatusResponse bankStatus = bankStatus(TransactionStatus.FAILED, null);
        given(bankClient.getChargeStatus(UUID)).willReturn(bankStatus);
        given(chargeStateWriter.applyReconcileResult(UUID, bankStatus)).willReturn(response());

        chargeReconcileService.reconcile(charge(TransactionStatus.UNKNOWN));

        verify(chargeIdempotencyStore).markExecutionStatus(UUID, TransactionStatus.FAILED);
        verify(chargeIdempotencyStore, never()).completeExecution(any(), any());
    }

    @Test
    @DisplayName("bank PROCESSING -> 시도 횟수만 증가, 확정 안 함")
    void bank_processing() {
        BankTransactionStatusResponse bankStatus = bankStatus(TransactionStatus.PROCESSING, null);
        given(bankClient.getChargeStatus(UUID)).willReturn(bankStatus);
        given(chargeStateWriter.applyReconcileResult(UUID, bankStatus)).willReturn(response());

        chargeReconcileService.reconcile(charge(TransactionStatus.PROCESSING));

        verify(chargeStateWriter).incrementReconcileAttempt(UUID);
        verify(chargeIdempotencyStore, never()).completeExecution(any(), any());
        verify(chargeIdempotencyStore, never()).markExecutionStatus(any(), any());
    }

    @Test
    @DisplayName("reconcile은 transactionUuid Redis lock 안에서 실행한다")
    void reconcile_usesChargeLock() {
        BankTransactionStatusResponse bankStatus = bankStatus(TransactionStatus.SUCCESS, BANK_TX_ID);
        given(bankClient.getChargeStatus(UUID)).willReturn(bankStatus);
        given(chargeStateWriter.applyReconcileResult(UUID, bankStatus)).willReturn(response());

        chargeReconcileService.reconcile(charge(TransactionStatus.UNKNOWN));

        verify(chargeLockManager).withChargeLock(eq(UUID), any());
    }

    private Transaction charge(TransactionStatus status) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.CHARGE)
                .status(status)
                .build();
    }

    private BankTransactionStatusResponse bankStatus(TransactionStatus status, Long bankTxId) {
        return new BankTransactionStatusResponse(
                UUID, bankTxId, status, null, LocalDateTime.of(2026, 5, 25, 10, 5));
    }

    private ChargeExecuteResponse response() {
        return new ChargeExecuteResponse(
                1L,
                TRANSACTION_ID,
                new BigDecimal("50000"),
                new BigDecimal("45000"),
                new BigDecimal("45000"),
                LocalDateTime.now());
    }
}
