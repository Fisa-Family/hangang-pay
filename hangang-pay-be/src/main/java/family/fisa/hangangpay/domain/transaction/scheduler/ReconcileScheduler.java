package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import java.util.EnumMap;
import family.fisa.hangangpay.domain.transaction.service.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.ExchangeStateWriter;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** PENDING/UNKNOWN 환전을 주기적으로 일관 reconcile 하는 배치 트리거 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileScheduler {

    public static final int MAX_RETRY = 10;
    private static final long RECONCILE_INTERVAL_MS = 300_000L; // 5분
    private static final long EXPIRE_INTERVAL_MS = 300_000L; // 5분
    private static final int INTENT_TTL_MINUTES = 10; // 이 시간 지난 PENDING은 만료

    private final TransactionRepository transactionRepository;
    private final ExchangeReconcileService exchangeReconcileService;
    private final ExchangeStateWriter stateWriter;

    /** PROCESSING/UNKNOWN을 bank 조회로 확정 */
    @Scheduled(fixedDelay = RECONCILE_INTERVAL_MS)
    // TODO @SchedulerLock(name = "exchangeReconcile")
    public void reconcileExchanges() {
        List<Transaction> targets = transactionRepository.findExchangeReconcileTargets(MAX_RETRY);
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
    }

    /** TTL 지난 PENDING intent를 EXPIRED 처리 */
    @Scheduled(fixedDelay = EXPIRE_INTERVAL_MS)
    // TODO @SchedulerLock(name = "exchangeExpire")
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
