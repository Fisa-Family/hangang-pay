package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconcileSchedulerTest {

    private static final String EXCHANGE_UUID = "33333333-3333-3333-3333-333333333333";

    @Mock private TransactionRepository transactionRepository;
    @Mock private ExchangeReconcileService exchangeReconcileService;
    @Mock private ExchangeStateWriter stateWriter;

    @InjectMocks private ReconcileScheduler scheduler;

    @Test
    @DisplayName("reconcile이 일시 오류(비즈니스 예외 아님)로 실패하면 retry 횟수를 1 올린다")
    void reconcileExchanges_incrementsRetryOnTransientFailure() {
        Transaction tx = exchangeProcessing();
        given(transactionRepository.findExchangeReconcileTargets(anyInt(), any()))
                .willReturn(List.of(tx));
        willThrow(new RuntimeException("bank 5xx"))
                .given(exchangeReconcileService)
                .reconcile(tx);

        scheduler.reconcileExchanges();

        verify(stateWriter).incrementRetry(EXCHANGE_UUID);
    }

    @Test
    @DisplayName("reconcile이 비즈니스 예외로 실패하면 retry 횟수를 올리지 않는다")
    void reconcileExchanges_doesNotIncrementRetryOnBusinessException() {
        Transaction tx = exchangeProcessing();
        given(transactionRepository.findExchangeReconcileTargets(anyInt(), any()))
                .willReturn(List.of(tx));
        willThrow(new BusinessException(TransactionErrorCode.EXCHANGE_NOT_FOUND))
                .given(exchangeReconcileService)
                .reconcile(tx);

        scheduler.reconcileExchanges();

        verify(stateWriter, never()).incrementRetry(any());
    }

    private Transaction exchangeProcessing() {
        return Transaction.builder()
                .transactionUuid(EXCHANGE_UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(TransactionStatus.PROCESSING)
                .amount(new BigDecimal("10000"))
                .reconcileAttemptCount(0)
                .build();
    }
}
