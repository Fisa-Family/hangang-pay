package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelExecutionStateWriter;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentExecutionStateWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnknownPaymentRecoveryScheduler {

    private final TransactionRepository transactionRepository;
    private final TransactionCommandService transactionCommandService;
    private final PaymentExecutionStateWriter paymentExecutionStateWriter;
    private final CancelExecutionStateWriter cancelExecutionStateWriter;

    /** PROCESSING이 "죽어서 미반영"으로 간주되는 임계 시간 (결제 특성상 5분) */
    private static final int PROCESSING_STALE_MINUTES = 5;

    /** 자동 복구 포기 임계 시도 횟수 */
    private static final int MAX_RECOVER_ATTEMPTS = 10;

    @Scheduled(cron = "0 * * * * *")
    public void resolveUnknownPayments() {
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
                transactionCommandService.recoverPayment(partyId, transactionUuid);
                log.info("결제 복구 성공: transactionUuid={}", transactionUuid);
            } catch (Exception e) {
                log.warn(
                        "결제 복구 실패 (다음 실행에 재시도): transactionUuid={}, reason={}",
                        transactionUuid,
                        e.getMessage());
            }
        }

        // 포기 대상(시도 횟수 == 한도) alert. ERROR 로그 1회 후 카운트를 올려 재알림을 막는다.
        for (Transaction abandoned :
                transactionRepository.findAbandonedProcessingByType(
                        TransactionType.PAYMENT, threshold, MAX_RECOVER_ATTEMPTS)) {
            log.error(
                    "[ALERT] 결제 자동 복구 포기 - 수기 확인 필요. transactionUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            paymentExecutionStateWriter.incrementRecoveryAttempt(abandoned.getTransactionUuid());
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void resolveUnknownCancels() {
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

                transactionCommandService.recoverCancel(
                        merchantPartyId, originalPaymentOpt.get().getId());

            } catch (Exception e) {
                // 개별 실패 — 전체 스케줄러를 중단하지 않고 다음 건 처리
                log.warn(
                        "취소 복구 실패 (다음 실행에 재시도): cancelUuid={}, reason={}",
                        cancelUuid,
                        e.getMessage());
            }
        }

        // 포기 대상 alert (원샷)
        for (Transaction abandoned :
                transactionRepository.findAbandonedProcessingByType(
                        TransactionType.CANCEL, threshold, MAX_RECOVER_ATTEMPTS)) {
            log.error(
                    "[ALERT] 취소 자동 복구 포기 - 수기 확인 필요. cancelUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            cancelExecutionStateWriter.incrementRecoveryAttempt(abandoned.getTransactionUuid());
        }
    }
}
