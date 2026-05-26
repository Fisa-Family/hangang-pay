package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.ExchangeStatusResponse;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.ReconcileResult;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** PENDING으로 남은 EXCHANGE를 bank 재조회해 SUCCESS / FAILED 로 변환하는 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeReconcileService {

    /** reconcile 시도 횟수 임계값. 도달 이후엔 배치/진입 모두 더 이상 시도하지 않음 */
    static final int MAX_RECONCILE_ATTEMPTS = 10;

    private final TransactionRepository transactionRepository;
    private final ExchangeStateWriter exchangeStateWriter;
    private final BankClient bankClient;

    /** 단건 reconcile */
    public ReconcileResult reconcile(Long transactionId) {
        // 1. transaction 조회 + 사전 검증
        Transaction tx =
                transactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.EXCHANGE_NOT_FOUND));

        if (tx.getStatus() != TransactionStatus.PENDING) {
            // 이미 마킹 끝난 거래
            log.info(
                    "reconcile 대상 아님(PENDING 아님). transactionId={}, status={}",
                    transactionId,
                    tx.getStatus());
            return ReconcileResult.SKIPPED;
        }

        // reconcile 횟수 임계값 초과
        if (tx.getReconcileAttemptCount() >= MAX_RECONCILE_ATTEMPTS) {
            log.error(
                    "reconcile 임계 도달 - 자동 시도 종료. 수동 처리 필요. transactionId={}, transactionUuid={}, attempt={}",
                    transactionId,
                    tx.getTransactionUuid(),
                    tx.getReconcileAttemptCount());
            return ReconcileResult.RECONCILE_ERROR;
        }

        // 2. 시도 횟수 증가 (별도 Tx로 commit - bank 호출이 실패해도 카운트는 유지)
        int newCount = exchangeStateWriter.incrementReconcileAttempt(transactionId);

        // 3. bank 상태 조회
        log.info(
                "reconcile bank 상태 조회 시작. transactionId={}, transactionUuid={}, attempt={}",
                transactionId,
                tx.getTransactionUuid(),
                newCount);
        Optional<ExchangeStatusResponse> bankStatus =
                bankClient.queryExchangeStatus(tx.getTransactionUuid());

        // 4. 응답에 따라 SUCCESS / FAILED 마킹
        if (bankStatus.isPresent()) {
            ExchangeStatusResponse status = bankStatus.get();

            exchangeStateWriter.completeExchange(
                    transactionId, status.txHash(), String.valueOf(status.bankTransactionId()));

            log.info(
                    "reconcile 성공 마킹. transactionId={}, transactionUuid={}, txHash={}",
                    transactionId,
                    tx.getTransactionUuid(),
                    status.txHash());
            return ReconcileResult.RECONCILED_SUCCESS;
        }

        exchangeStateWriter.failExchange(transactionId);
        log.info(
                "reconcile 실패 마킹(bank에 거래 없음). transactionId={}, transactionUuid={}",
                transactionId,
                tx.getTransactionUuid());
        return ReconcileResult.RECONCILED_FAILED;
    }
}
