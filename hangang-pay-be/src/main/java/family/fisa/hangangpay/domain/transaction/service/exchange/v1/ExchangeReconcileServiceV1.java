package family.fisa.hangangpay.domain.transaction.service.exchange.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BankExchangeStatus;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeLockManager;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** PENDING으로 남은 EXCHANGE를 bank 재조회해 SUCCESS / FAILED 로 변환하는 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeReconcileServiceV1 implements ExchangeReconcileService {

    private final ExchangeStateWriter stateWriter;
    private final ExchangeIdempotencyStore idempotencyStore;
    private final BankClient bankClient;
    private final ExchangeLockManager exchangeLockManager;

    /** 한 건 reconcile: 분산 락 확보 후 bank 조회 결과로 확정 (실행 흐름과 동일 uuid 직렬화) */
    public void reconcile(Transaction tx) {
        String uuid = tx.getTransactionUuid();
        exchangeLockManager.withExchangeLock(
                uuid,
                () -> {
                    reconcileLocked(uuid);
                    return null;
                });
    }

    private void reconcileLocked(String uuid) {
        BankExchangeStatus s = bankClient.getStatus(uuid);

        switch (s.status()) {
            case SUCCESS -> {
                ExchangeExecuteResponse resp =
                        stateWriter.markSuccess(uuid, null, String.valueOf(s.bankTransactionId()));
                idempotencyStore.completeExecution(uuid, resp);
            }
            case FAILED, NOT_FOUND -> { // NOT_FOUND: 미도달 확정(토큰 미차감) → FAILED 안전
                stateWriter.markFailed(uuid);
                idempotencyStore.failExecution(uuid);
            }
            case PENDING -> stateWriter.incrementRetry(uuid); // bank 처리중 → 다음 주기
        }
    }
}
