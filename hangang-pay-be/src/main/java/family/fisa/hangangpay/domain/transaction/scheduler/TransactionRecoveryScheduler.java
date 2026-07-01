package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelCommandService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentCommandService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UNKNOWN/오래된 PROCESSING 거래를 은행 재조회로 확정(복구)하는 스케줄러.
 *
 * <p>결제·취소 복구(1분 주기)와 환전 reconcile(5분 주기)을 함께 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionRecoveryScheduler {

    /** PROCESSING이 "죽어서 미반영"으로 간주되는 임계 시간 (결제 특성상 5분) */
    private static final int PROCESSING_STALE_MINUTES = 5;

    /** 결제/취소 자동 복구 포기 임계 시도 횟수 */
    private static final int MAX_RECOVER_ATTEMPTS = 10;

    /** 환전 reconcile 재시도 한도 */
    private static final int MAX_RETRY = 10;

    private final TransactionRepository transactionRepository;
    private final PaymentCommandService paymentCommandService;
    private final CancelCommandService cancelCommandService;
    private final PaymentStateWriter paymentStateWriter;
    private final CancelStateWriter cancelStateWriter;
    private final ExchangeReconcileService exchangeReconcileService;
    private final ExchangeStateWriter exchangeStateWriter;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "resolveUnknownPayments", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void recoverPayments() {
        // UNKNOWN 전체 + 오래된 PROCESSING(원본 시도가 죽어 미반영된 건, 시도 한도 미만)을 함께 복구 대상으로 모은다.
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PROCESSING_STALE_MINUTES);

        List<Transaction> targets =
                new ArrayList<>(
                        transactionRepository.findAllUnknownByType(TransactionType.PAYMENT));
        targets.addAll(
                transactionRepository.findStaleProcessingByType(
                        TransactionType.PAYMENT, threshold, MAX_RECOVER_ATTEMPTS));

        log.info("결제 복구 스케줄러 실행: 대상 건수={}", targets.size());

        for (Transaction transaction : targets) {
            String transactionUuid = transaction.getTransactionUuid();
            Long partyId = transaction.getFromParty().getId();
            try {
                paymentCommandService.recoverPayment(partyId, transactionUuid);
                log.info("결제 복구 성공: transactionUuid={}", transactionUuid);
            } catch (Exception e) {
                log.warn(
                        "결제 복구 실패 (다음 실행에 재시도): transactionUuid={}, reason={}",
                        transactionUuid,
                        e.getMessage());
            }
        }

        // 포기 대상(시도 횟수 == 한도) alert 후 EXPIRED 터미널로 닫는다.
        // EXPIRED는 PROCESSING이 아니므로 다음 주기 sweep·재알림에서 자연히 제외된다.
        for (Transaction abandoned :
                transactionRepository.findAbandonedProcessingByType(
                        TransactionType.PAYMENT, threshold, MAX_RECOVER_ATTEMPTS)) {
            log.error(
                    "[ALERT] 결제 자동 복구 포기 - 수기 확인 필요. transactionUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            paymentStateWriter.markExpired(abandoned.getTransactionUuid());
        }
    }

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "resolveUnknownCancels", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void recoverCancels() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PROCESSING_STALE_MINUTES);

        // UNKNOWN 전체 + 오래된 PROCESSING CANCEL(시도 한도 미만)을 함께 복구 대상으로 모은다.
        List<Transaction> targets =
                new ArrayList<>(transactionRepository.findAllUnknownByType(TransactionType.CANCEL));
        targets.addAll(
                transactionRepository.findStaleProcessingByType(
                        TransactionType.CANCEL, threshold, MAX_RECOVER_ATTEMPTS));

        log.info("취소 복구 스케줄러 실행: 대상 건수={}", targets.size());

        for (Transaction cancelTx : targets) {
            String cancelUuid = cancelTx.getTransactionUuid();
            // CANCEL의 fromParty = 가맹점 (createCancel이 방향을 뒤집음)
            Long merchantPartyId = cancelTx.getFromParty().getId();

            try {
                // originalTransactionUuid 로 원복 PAYMENT 조회 - recoverCancel 은 원본 id를 요구
                Optional<Transaction> originalPaymentOpt =
                        transactionRepository.findByTransactionUuid(
                                cancelTx.getOriginalTransactionUuid());

                if (originalPaymentOpt.isEmpty()) {
                    // 원본 PAYMENT가 없으면 데이터 정합성 문제 — 재시도해도 해결 안 되므로 ERROR
                    log.error(
                            "취소 복구 불가: 원본 PAYMENT 없음. cancelUuid={}, originalUuid={}",
                            cancelUuid,
                            cancelTx.getOriginalTransactionUuid());
                    continue;
                }

                cancelCommandService.recoverCancel(
                        merchantPartyId, originalPaymentOpt.get().getId());

            } catch (Exception e) {
                // 개별 실패 — 전체 스케줄러를 중단하지 않고 다음 건 처리
                log.warn(
                        "취소 복구 실패 (다음 실행에 재시도): cancelUuid={}, reason={}",
                        cancelUuid,
                        e.getMessage());
            }
        }

        // 포기 대상 alert 후 EXPIRED 터미널로 닫는다 (다음 주기 sweep·재알림에서 제외)
        for (Transaction abandoned :
                transactionRepository.findAbandonedProcessingByType(
                        TransactionType.CANCEL, threshold, MAX_RECOVER_ATTEMPTS)) {
            log.error(
                    "[ALERT] 취소 자동 복구 포기 - 수기 확인 필요. cancelUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            cancelStateWriter.markExpired(abandoned.getTransactionUuid());
        }
    }

    /** PROCESSING/UNKNOWN 환전을 bank 조회로 확정 */
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
            } catch (BusinessException ex) {
                // 비즈니스 예외(검증 실패 등)는 재시도해도 동일 결과 → 재시도 예산을 올리지 않는다.
                log.warn(
                        "reconcile 비즈니스 예외(재시도 제외). transactionUuid={}, code={}",
                        tx.getTransactionUuid(),
                        ex.getCode().getCode());
            } catch (RuntimeException ex) {
                // 일시 오류(은행 5xx/네트워크 등) → 재시도 예산을 1 올려 결국 포기 대상으로 수렴시킨다.
                log.error("reconcile 처리 실패(일시). transactionUuid={}", tx.getTransactionUuid(), ex);
                exchangeStateWriter.incrementRetry(tx.getTransactionUuid());
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
            exchangeStateWriter.markExpired(abandoned.getTransactionUuid());
        }
    }
}
