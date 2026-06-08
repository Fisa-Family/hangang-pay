package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.ExchangeStatusResponse;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.ReconcileResult;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeReconcileServiceTest {

    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TX_HASH = "0xabc123";
    private static final Long BANK_TX_ID = 999L;
    private static final String BANK_TX_ID_STR = "999";

    @Mock TransactionRepository transactionRepository;
    @Mock ExchangeStateWriter stateWriter;
    @Mock ExchangeIdempotencyStore idempotencyStore;
    @Mock BankClient bankClient;

    @InjectMocks ExchangeReconcileService exchangeReconcileService;

    /** 주어진 attempt count로 UNKNOWN transaction 생성 (reconcile 대상) */
    private Transaction unknownTransaction(int attemptCount) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(TransactionStatus.UNKNOWN)
                .reconcileAttemptCount(attemptCount)
                .build();
    }

    /** SUCCESS / FAILED 등 종결 상태 transaction (reconcile 대상 아님) */
    private Transaction transactionWithStatus(TransactionStatus status) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(status)
                .reconcileAttemptCount(0)
                .build();
    }

    private ExchangeStatusResponse bankSuccessResponse() {
        return new ExchangeStatusResponse(UUID, BANK_TX_ID, TX_HASH);
    }

    private ExchangeExecuteResponse completedResponse() {
        return ExchangeExecuteResponse.builder()
                .transactionId(TRANSACTION_ID)
                .transactionUuid(UUID)
                .status(TransactionStatus.SUCCESS)
                .txHash(TX_HASH)
                .build();
    }

    @Test
    @DisplayName("bank가 SUCCESS면 RECONCILED_SUCCESS + completeExchange + 멱등 completeExecution")
    void reconcile_bank_success() {
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(unknownTransaction(0)));
        when(stateWriter.incrementReconcileAttempt(TRANSACTION_ID)).thenReturn(1);
        when(bankClient.queryExchangeStatus(UUID)).thenReturn(Optional.of(bankSuccessResponse()));
        ExchangeExecuteResponse resp = completedResponse();
        when(stateWriter.completeExchange(TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR))
                .thenReturn(resp);

        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        assertThat(result).isEqualTo(ReconcileResult.RECONCILED_SUCCESS);
        verify(stateWriter).incrementReconcileAttempt(TRANSACTION_ID);
        verify(stateWriter).completeExchange(TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR);
        verify(idempotencyStore).completeExecution(UUID, resp);
        verify(stateWriter, never()).failExchange(any());
        verify(idempotencyStore, never()).failExecution(any());
    }

    @Test
    @DisplayName("bank가 NOT_FOUND면 RECONCILED_FAILED + failExchange + 멱등 failExecution")
    void reconcile_bank_not_found() {
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(unknownTransaction(0)));
        when(stateWriter.incrementReconcileAttempt(TRANSACTION_ID)).thenReturn(1);
        when(bankClient.queryExchangeStatus(UUID)).thenReturn(Optional.empty());

        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        assertThat(result).isEqualTo(ReconcileResult.RECONCILED_FAILED);
        verify(stateWriter).incrementReconcileAttempt(TRANSACTION_ID);
        verify(stateWriter).failExchange(TRANSACTION_ID);
        verify(idempotencyStore).failExecution(UUID);
        verify(stateWriter, never()).completeExchange(any(Long.class), any(), any());
    }

    @Test
    @DisplayName("이미 SUCCESS 상태면 SKIPPED + bank/writer 호출 안 됨")
    void reconcile_already_success() {
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(transactionWithStatus(TransactionStatus.SUCCESS)));

        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        assertThat(result).isEqualTo(ReconcileResult.SKIPPED);
        verify(bankClient, never()).queryExchangeStatus(any());
        verify(stateWriter, never()).incrementReconcileAttempt(any());
        verify(stateWriter, never()).completeExchange(any(Long.class), any(), any());
        verify(stateWriter, never()).failExchange(any());
    }

    @Test
    @DisplayName("PENDING 상태도 SKIPPED (단일 Tx 이후 PENDING은 reconcile 대상 아님)")
    void reconcile_pending_skipped() {
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(transactionWithStatus(TransactionStatus.PENDING)));

        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        assertThat(result).isEqualTo(ReconcileResult.SKIPPED);
        verify(bankClient, never()).queryExchangeStatus(any());
        verify(stateWriter, never()).incrementReconcileAttempt(any());
    }

    @Test
    @DisplayName("attempt가 임계값(10) 도달이면 RECONCILE_ERROR + bank/increment 호출 안 됨")
    void reconcile_attempt_threshold_reached() {
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(unknownTransaction(10)));

        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        assertThat(result).isEqualTo(ReconcileResult.RECONCILE_ERROR);
        verify(stateWriter, never()).incrementReconcileAttempt(any());
        verify(bankClient, never()).queryExchangeStatus(any());
    }

    @Test
    @DisplayName("존재하지 않는 transactionId면 EXCHANGE_NOT_FOUND 예외")
    void reconcile_transaction_not_found() {
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeReconcileService.reconcile(TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
    }
}
