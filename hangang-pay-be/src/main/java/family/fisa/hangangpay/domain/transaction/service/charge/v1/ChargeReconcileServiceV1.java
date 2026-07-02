package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeLockManager;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeReconcileServiceV1 implements ChargeReconcileService {

    private final BankClient bankClient;
    private final ChargeLockManager chargeLockManager;
    private final ChargeStateWriter chargeStateWriter;
    private final ChargeIdempotencyStore chargeIdempotencyStore;

    /** 배치: 스케줄러가 고른 대상을 락만 잡고 재조회 확정한다. 소유권/rate-limit 없음. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcile(Transaction tx) {
        String transactionUuid = tx.getTransactionUuid();
        chargeLockManager.withChargeLock(
                transactionUuid,
                () -> {
                    core(transactionUuid);
                    return null;
                });
    }

    /** 공통 core: bank 재조회 → 상태 반영 → 종단이면 Redis 멱등 record 정리, PROCESSING이면 시도 횟수만 증가. */
    private void core(String transactionUuid) {
        BankTransactionStatusResponse bankStatus = reconcileFromBank(transactionUuid);
        ChargeExecuteResponse response =
                chargeStateWriter.applyReconcileResult(transactionUuid, bankStatus);

        if (bankStatus.status() == TransactionStatus.SUCCESS) {
            chargeIdempotencyStore.completeExecution(transactionUuid, response);
        } else if (bankStatus.status() == TransactionStatus.FAILED) {
            // 충전 멱등 스토어는 failExecution이 없으므로 상태만 FAILED로 마킹한다(execute 종단 실패와 동일).
            chargeIdempotencyStore.markExecutionStatus(transactionUuid, TransactionStatus.FAILED);
        } else {
            // 은행이 아직 처리 중 → 시도 횟수만 올리고 다음 주기 재시도 (cap 도달 시 sweep 제외)
            chargeStateWriter.incrementReconcileAttempt(transactionUuid);
        }
    }

    /**
     * bankClient 호출 전 종료된 요청은 PROCESSING 레코드가 저장되고 고아 상태가 된다. 이 경우 은행 조회가 404 - NOT FOUND로 반환된다.
     */
    private BankTransactionStatusResponse reconcileFromBank(String transactionUuid) {
        try {
            // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyReconcileResult가 반영한다.
            return bankClient.getChargeStatus(transactionUuid);
        } catch (BankException e) {
            // 2. 404가 아니면 (5xx 등) 일시적 오류 → 다시 던져서 다음 sweep에 재시도한다.
            if (!e.getError().isStatus(HttpStatus.NOT_FOUND)) {
                throw e;
            }
            // 3. 404는 은행 원장에 기록 자체가 없다 → 은행 도달 전 사망으로 간주해 FAILED 확정(플랫폼 책임).
            return BankTransactionStatusResponse.failed(transactionUuid);
        }
    }
}
