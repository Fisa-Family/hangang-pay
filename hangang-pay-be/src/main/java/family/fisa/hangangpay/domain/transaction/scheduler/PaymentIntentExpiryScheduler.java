package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TTL 지난 PENDING 결제 intent를 EXPIRED 처리하는 배치 트리거 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentIntentExpiryScheduler {

    private static final int INTENT_TTL_MINUTES = 10;

    private final TransactionRepository transactionRepository;

    /** TTL 지난 PENDING intent를 EXPIRED 처리 */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "expireStalePaymentIntents", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void expireStaleIntents() {
        LocalDateTime now = LocalDateTime.now();
        int expired =
                transactionRepository.expireStalePendingPaymentIntents(
                        now.minusMinutes(INTENT_TTL_MINUTES), now);

        if (expired > 0) {
            log.info("결제 intent 만료 배치 완료. count={}", expired);
        } else {
            log.info("결제 intent 만료가 없습니다.");
        }
    }
}
