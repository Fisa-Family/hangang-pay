package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.dto.ReconcileResult;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.ExchangeReconcileService;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Orphan PENDING 환전 일괄 reconcile 배치. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileScheduler {
    /** 배치 주기 (5분) */
    private static final long BATCH_INTERVAL_MS = 300_000L;

    /** PENDING이 orphan으로 간주되는 임계 시간 */
    private static final int ORPHAN_THRESHOLD_MINUTES = 5;

    /** reconcile 시도 횟수 임계값 - 이상은 배치 대상에서 제외 */
    private static final int MAX_RECONCILE_ATTEMPTS = 10;

    private final TransactionRepository transactionRepository;
    private final ExchangeReconcileService exchangeReconcileService;

    /** 주적으로 orphan PENDING을 reconcile 한다. */
    @Scheduled(fixedDelay = BATCH_INTERVAL_MS)
    public void reconcileOrphanPendings() {
        // 1. 임계 시간 + 시도 횟수 조건으로 대상 transaction id 조회
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ORPHAN_THRESHOLD_MINUTES);
        List<Long> targets =
                transactionRepository.findPendingExchangeIdsForReconcile(
                        threshold, MAX_RECONCILE_ATTEMPTS);

        if (targets.isEmpty()) {
            return;
        }

        log.info("orphan PENDING 환전 reconcile 배치 시작. count={}", targets.size());

        // 2. 각 transaction에 대해 reconcile 위임. 결과를 enum별로 집계
        Map<ReconcileResult, Integer> stats = new EnumMap<>(ReconcileResult.class);
        for (Long transactionId : targets) {
            try {
                ReconcileResult result = exchangeReconcileService.reconcile(transactionId);
                stats.merge(result, 1, Integer::sum);
            } catch (RuntimeException ex) {
                // 한 건 실패가 배치 전체를 중단시키지 않도록 격리
                log.error("reconcile 배치 처리 중 예외. transactionId={}", transactionId, ex);
            }
        }

        // 3. 집계 결과 로그
        log.info("orphan PENDING 환전 reconcile 배치 완료. stats={}", stats);
    }
}
