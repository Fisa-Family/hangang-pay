package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentIntentExpirySchedulerTest {

    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private PaymentIntentExpiryScheduler scheduler;

    @Test
    @DisplayName("TTL 지난 PENDING 결제 intent 만료를 repository 조건부 UPDATE에 위임한다")
    void expireStaleIntents_delegatesToRepository() {
        given(transactionRepository.expireStalePendingPaymentIntents(any(), any())).willReturn(2);

        scheduler.expireStaleIntents();

        verify(transactionRepository).expireStalePendingPaymentIntents(any(), any());
    }
}
