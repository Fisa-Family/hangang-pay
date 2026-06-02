package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
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

    @Scheduled(cron = "0 * * * * *")
    public void resolveUnknownPayments() {
        List<Transaction> targets =
                transactionRepository.findAllUnknownByType(TransactionType.PAYMENT);
        log.info("UNKNOWN 결제 복구 스케줄러 실행: 대상 건수={}", targets.size());

        for (Transaction transaction : targets) {
            String transactionUuid = transaction.getTransactionUuid();
            Long partyId = transaction.getFromParty().getId();
            try {
                transactionCommandService.recoverPayment(partyId, transactionUuid);
                log.info("UNKNOWN 복구 성공: transactionUuid={}", transactionUuid);
            } catch (Exception e) {
                log.warn(
                        "UNKNOWN 복구 실패 (다음 실행에 재시도): transactionUuid={}, reason={}",
                        transactionUuid,
                        e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void resolveUnknownCancels() {
        // 1. UNKNOWN 상태 CANCEL 전체 조회 (fromParty fetch join 포함)
        List<Transaction> targets =
                transactionRepository.findAllUnknownByType(TransactionType.CANCEL);
        log.info("UNKNOWN 취소 복구 스케줄러 실행: 대상 건수={}", targets.size());

        for (Transaction cancelTx : targets) {
            String cancelUuid = cancelTx.getTransactionUuid();

            // 2. CANCEL의 fromParty = 가맹점 (createCancel이 방향을 뒤집음)
            Long merchantPartyId = cancelTx.getFromParty().getId();

            try {
                // 3. originalTransactionUuid 로 원복 PAYMENT 조회 - recoverCancel 은 id를 요구
                Optional<Transaction> originalPaymentOpt =
                        transactionRepository.findByTransactionUuid(
                                cancelTx.getOriginalTransactionUuid());

                if (originalPaymentOpt.isEmpty()) {
                    // 원본 PAYMENT가 없으면 데이터 정합성 문제 — 재시도해도 해결 안 되므로 ERROR
                    log.error(
                            "UNKNOWN 취소 복구 불가: 원본 PAYMENT 없음. cancelUuid={}, originalUuid={}",
                            cancelUuid,
                            cancelTx.getOriginalTransactionUuid());
                    continue;
                }

                // 4. 복구 실행
                transactionCommandService.recoverCancel(
                        merchantPartyId, originalPaymentOpt.get().getId());

            } catch (Exception e) {
                // 5. 개별 실패 — 전체 스케줄러를 중단하지 않고 다음 건 처리
                log.warn(
                        "UNKNOWN 취소 복구 실패 (다음 실행에 재시도): cancelUuid={}, reason={}",
                        cancelUuid,
                        e.getMessage());
            }
        }
    }
}
