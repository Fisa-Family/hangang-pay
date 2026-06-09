package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeExecutionWriter;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TTL 지난 PENDING 충전 intent를 EXPIRED 처리하는 배치 트리거 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeIntentExpiryScheduler {

    private static final int INTENT_TTL_MINUTES = 10; // 이 시간 지난 PENDING은 만료

    private final TransactionRepository transactionRepository;
    private final ChargeExecutionWriter chargeExecutionWriter;

    /** TTL 지난 PENDING intent를 EXPIRED 처리 */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "expireStaleChargeIntents", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void expireStaleIntents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(INTENT_TTL_MINUTES);
        List<Transaction> targets = transactionRepository.findStalePendingChargeIntents(threshold);
        if (targets.isEmpty()) {
            return;
        }
        log.info("충전 intent 만료 배치 시작. count={}", targets.size());
        for (Transaction tx : targets) {
            try {
                chargeExecutionWriter.markExpired(tx.getTransactionUuid());
            } catch (RuntimeException ex) {
                log.error("충전 intent 만료 처리 실패. transactionUuid={}", tx.getTransactionUuid(), ex);
            }
        }
        log.info("충전 intent 만료 배치 완료. count={}", targets.size());
    }
}
