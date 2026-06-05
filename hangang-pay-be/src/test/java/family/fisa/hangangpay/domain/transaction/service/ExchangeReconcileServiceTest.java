package family.fisa.hangangpay.domain.transaction.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BankExchangeStatus;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeReconcileServiceTest {

    @Mock ExchangeStateWriter stateWriter;
    @Mock ExchangeIdempotencyStore idempotencyStore;
    @Mock BankClient bankClient;

    @InjectMocks ExchangeReconcileService exchangeReconcileService;

    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TX_HASH = "0xabc123";
    private static final Long BANK_TX_ID = 999L;
    private static final String BANK_TX_ID_STR = "999";

    /** reconcile 대상은 PROCESSING/UNKNOWN */
    private Transaction exchange(TransactionStatus status) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("bank SUCCESS -> markSuccess + completeExecution")
    void bank_success() {
        when(bankClient.getStatus(UUID))
                .thenReturn(
                        new BankExchangeStatus(
                                BankExchangeStatus.Status.SUCCESS, TX_HASH, BANK_TX_ID));
        ExchangeExecuteResponse resp =
                ExchangeExecuteResponse.builder()
                        .transactionId(TRANSACTION_ID)
                        .transactionUuid(UUID)
                        .status(TransactionStatus.SUCCESS)
                        .txHash(TX_HASH)
                        .build();
        when(stateWriter.markSuccess(UUID, TX_HASH, BANK_TX_ID_STR)).thenReturn(resp);

        exchangeReconcileService.reconcile(exchange(TransactionStatus.UNKNOWN));

        verify(stateWriter).markSuccess(UUID, TX_HASH, BANK_TX_ID_STR);
        verify(idempotencyStore).completeExecution(UUID, resp);
        verify(stateWriter, never()).markFailed(any());
    }

    @Test
    @DisplayName("bank FAILED -> markFailed + failExecution")
    void bank_failed() {
        when(bankClient.getStatus(UUID))
                .thenReturn(new BankExchangeStatus(BankExchangeStatus.Status.FAILED, null, null));

        exchangeReconcileService.reconcile(exchange(TransactionStatus.UNKNOWN));

        verify(stateWriter).markFailed(UUID);
        verify(idempotencyStore).failExecution(UUID);
        verify(stateWriter, never()).markSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("bank NOT_FOUND -> 미도달 확정 markFailed + failExecution")
    void bank_not_found() {
        when(bankClient.getStatus(UUID)).thenReturn(BankExchangeStatus.notFound());

        exchangeReconcileService.reconcile(exchange(TransactionStatus.PROCESSING));

        verify(stateWriter).markFailed(UUID);
        verify(idempotencyStore).failExecution(UUID);
    }

    @Test
    @DisplayName("bank PENDING -> incrementRetry, 확정 안 함")
    void bank_pending() {
        when(bankClient.getStatus(UUID))
                .thenReturn(new BankExchangeStatus(BankExchangeStatus.Status.PENDING, null, null));

        exchangeReconcileService.reconcile(exchange(TransactionStatus.PROCESSING));

        verify(stateWriter).incrementRetry(UUID);
        verify(stateWriter, never()).markSuccess(any(), any(), any());
        verify(stateWriter, never()).markFailed(any());
    }
}
