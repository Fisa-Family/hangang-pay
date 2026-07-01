package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntentExpirySchedulerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private ChargeStateWriter chargeStateWriter;
    @Mock private ExchangeStateWriter exchangeStateWriter;

    @InjectMocks private IntentExpiryScheduler scheduler;

    @Test
    @DisplayName("TTL 지난 PENDING 결제 intent 만료를 repository 조건부 UPDATE에 위임한다")
    void expirePaymentIntents_delegatesToRepository() {
        given(transactionRepository.expireStalePendingPaymentIntents(any(), any())).willReturn(2);

        scheduler.expirePaymentIntents();

        verify(transactionRepository).expireStalePendingPaymentIntents(any(), any());
    }
}
