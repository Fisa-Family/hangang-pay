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
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
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
    @Mock BankClient bankClient;

    @InjectMocks ExchangeReconcileService exchangeReconcileService;

    /** 주어진 attempt count로 PENDING transaction 생성 */
    private Transaction pendingTransaction(int attemptCount) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(TransactionStatus.PENDING)
                .reconcileAttemptCount(attemptCount)
                .build();
    }

    /** SUCCESS / FAILED 등 종결 상태 transaction */
    private Transaction transactionWithStatus(TransactionStatus status) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(status)
                .reconcileAttemptCount(0)
                .build();
    }

    /** bank의 SUCCESS 응답 (두 ledger 모두 있음) */
    private ExchangeStatusResponse bankSuccessResponse() {
        return new ExchangeStatusResponse(UUID, BANK_TX_ID, TX_HASH);
    }

    @Test
    @DisplayName("bank가 SUCCESS면 RECONCILED_SUCCESS 반환 + completeExchange 호출")
    void reconcile_bank_success() {
        // given
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(pendingTransaction(0)));
        when(stateWriter.incrementReconcileAttempt(TRANSACTION_ID)).thenReturn(1);
        when(bankClient.queryExchangeStatus(UUID)).thenReturn(Optional.of(bankSuccessResponse()));

        // when
        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        // then
        assertThat(result).isEqualTo(ReconcileResult.RECONCILED_SUCCESS);
        verify(stateWriter).incrementReconcileAttempt(TRANSACTION_ID);
        verify(stateWriter).completeExchange(TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR);
        verify(stateWriter, never()).failExchange(any());
    }

    @Test
    @DisplayName("bank가 NOT_FOUND면 RECONCILED_FAILED 반환 + failExchange 호출")
    void reconcile_bank_not_found() {
        // given
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(pendingTransaction(0)));
        when(stateWriter.incrementReconcileAttempt(TRANSACTION_ID)).thenReturn(1);
        when(bankClient.queryExchangeStatus(UUID)).thenReturn(Optional.empty());

        // when
        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        // then
        assertThat(result).isEqualTo(ReconcileResult.RECONCILED_FAILED);
        verify(stateWriter).incrementReconcileAttempt(TRANSACTION_ID);
        verify(stateWriter).failExchange(TRANSACTION_ID);
        verify(stateWriter, never()).completeExchange(any(), any(), any());
    }

    @Test
    @DisplayName("이미 SUCCESS 상태면 SKIPPED + bank/writer 호출 안 됨")
    void reconcile_already_success() {
        // given
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(transactionWithStatus(TransactionStatus.SUCCESS)));

        // when
        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        // then
        assertThat(result).isEqualTo(ReconcileResult.SKIPPED);
        verify(bankClient, never()).queryExchangeStatus(any());
        verify(stateWriter, never()).incrementReconcileAttempt(any());
        verify(stateWriter, never()).completeExchange(any(), any(), any());
        verify(stateWriter, never()).failExchange(any());
    }

    @Test
    @DisplayName("이미 FAILED 상태면 SKIPPED + bank/writer 호출 안 됨")
    void reconcile_already_failed() {
        // given
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(transactionWithStatus(TransactionStatus.FAILED)));

        // when
        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        // then
        assertThat(result).isEqualTo(ReconcileResult.SKIPPED);
        verify(bankClient, never()).queryExchangeStatus(any());
        verify(stateWriter, never()).incrementReconcileAttempt(any());
    }

    @Test
    @DisplayName("attempt가 임계값(10) 도달이면 RECONCILE_ERROR + bank/increment 호출 안 됨")
    void reconcile_attempt_threshold_reached() {
        // given
        when(transactionRepository.findById(TRANSACTION_ID))
                .thenReturn(Optional.of(pendingTransaction(10)));

        // when
        ReconcileResult result = exchangeReconcileService.reconcile(TRANSACTION_ID);

        // then
        assertThat(result).isEqualTo(ReconcileResult.RECONCILE_ERROR);
        verify(stateWriter, never()).incrementReconcileAttempt(any());
        verify(bankClient, never()).queryExchangeStatus(any());
    }

    @Test
    @DisplayName("존재하지 않는 transactionId면 EXCHANGE_NOT_FOUND 예외")
    void reconcile_transaction_not_found() {
        // given
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> exchangeReconcileService.reconcile(TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
    }
}
