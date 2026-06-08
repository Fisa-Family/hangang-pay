package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** PROCESSING/UNKNOWN 환전을 주기적으로 일관 reconcile 하는 배치 트리거 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileScheduler {

    public static final int MAX_RETRY = 10;
    private static final int INTENT_TTL_MINUTES = 10; // 이 시간 지난 PENDING은 만료
    private static final int PROCESSING_STALE_MINUTES =
            5; // 이 시간 안 지난 PROCESSING은 라이브 실행 중으로 보고 건드리지 않음

    private final TransactionRepository transactionRepository;
    private final ExchangeReconcileService exchangeReconcileService;
    private final ExchangeStateWriter stateWriter;

    /** PROCESSING/UNKNOWN을 bank 조회로 확정 */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "reconcileExchanges", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void reconcileExchanges() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PROCESSING_STALE_MINUTES);

        List<Transaction> targets =
                transactionRepository.findExchangeReconcileTargets(MAX_RETRY, threshold);
        if (targets.isEmpty()) {
            return;
        }
        log.info("환전 reconcile 배치 시작. count={}", targets.size());
        for (Transaction tx : targets) {
            try {
                exchangeReconcileService.reconcile(tx);
            } catch (RuntimeException ex) {
                log.error("reconcile 처리 실패. transactionUuid={}", tx.getTransactionUuid(), ex);
            }
        }
        log.info("환전 reconcile 배치 완료. count={}", targets.size());

        // 시도 한도를 소진한 PROCESSING/UNKNOWN을 alert 후 EXPIRED 터미널로 닫는다.
        for (Transaction abandoned :
                transactionRepository.findExchangeAbandonedTargets(MAX_RETRY)) {
            log.error(
                    "[ALERT] 환전 자동 reconcile 포기 - 수기 확인 필요. transactionUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            stateWriter.markExpired(abandoned.getTransactionUuid());
        }
    }

    /** TTL 지난 PENDING intent를 EXPIRED 처리 */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "expireStaleIntents", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void expireStaleIntents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(INTENT_TTL_MINUTES);
        List<Transaction> targets =
                transactionRepository.findStalePendingExchangeIntents(threshold);
        if (targets.isEmpty()) {
            return;
        }
        log.info("환전 intent 만료 배치 시작. count={}", targets.size());
        for (Transaction tx : targets) {
            try {
                stateWriter.markExpired(tx.getTransactionUuid());
            } catch (RuntimeException ex) {
                log.error("intent 만료 처리 실패. transactionUuid={}", tx.getTransactionUuid(), ex);
            }
        }
        log.info("환전 intent 만료 배치 완료. count={}", targets.size());
    }
}
