package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
import java.util.List;
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

    @Scheduled(cron = "*/10 * * * * *")
    public void resolveUnknownPayments() {
        List<Transaction> targets = transactionRepository.findAllUnknownPayments();
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
}
