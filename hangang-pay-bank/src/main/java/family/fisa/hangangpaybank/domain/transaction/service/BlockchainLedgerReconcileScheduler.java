package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Slf4j
@Component
public class BlockchainLedgerReconcileScheduler {

    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final BlockchainOutboxRepository blockchainOutboxRepository;
    private final ContractCallService contractCallService;
    private final BlockchainLedgerStateWriter ledgerStateWriter;
    private final long staleMinutes;
    private final int batchSize;

    public BlockchainLedgerReconcileScheduler(
            BlockchainLedgerRepository blockchainLedgerRepository,
            BlockchainOutboxRepository blockchainOutboxRepository,
            ContractCallService contractCallService,
            BlockchainLedgerStateWriter ledgerStateWriter,
            @Value("${blockchain.reconcile.stale-minutes:10}") long staleMinutes,
            @Value("${blockchain.reconcile.batch-size:50}") int batchSize) {
        this.blockchainLedgerRepository = blockchainLedgerRepository;
        this.blockchainOutboxRepository = blockchainOutboxRepository;
        this.contractCallService = contractCallService;
        this.ledgerStateWriter = ledgerStateWriter;
        this.staleMinutes = staleMinutes;
        this.batchSize = batchSize;
    }

    /** 스케줄러 진입점. stale 기준 시각을 계산해 reconcileOnce에 위임한다. */
    @Scheduled(fixedDelayString = "${blockchain.reconcile.delay-ms:60000}")
    public void reconcile() {
        reconcileOnce(LocalDateTime.now().minusMinutes(staleMinutes), batchSize);
    }

    /**
     * 단일 reconcile 사이클. 4개 단계를 순서대로 실행한다.
     *
     * <p>실행 순서가 중요하다. outbox 재발행(1단계)을 먼저 해야 2단계에서 PENDING ledger를 만났을 때
     * outbox가 이미 NEW 상태로 복구되어 있어 중복 재발행을 막을 수 있다.
     */
    void reconcileOnce(LocalDateTime updatedBefore, int limit) {
        // 1. FAILED outbox → NEW 재전환 (OutboxPublisherScheduler가 다시 발행하도록)
        reopenFailedOutboxes(updatedBefore, limit);
        // 2. PENDING ledger — txHash 없으면 outbox 재발행 유도, 있으면 receipt 직접 조회
        reconcilePendingLedgers(updatedBefore, limit);
        // 3. SUBMITTED ledger — txHash로 receipt 재조회 (프로세스 재시작 후 이어받기)
        reconcileSubmittedLedgers(updatedBefore, limit);
        // 4. FAILED이지만 txHash 있는 ledger — 체인에선 성공했을 수 있으므로 재확인
        reconcileFailedLedgersWithTxHash(updatedBefore, limit);
    }

    /** FAILED 상태로 멈춘 outbox를 NEW로 되돌려 OutboxPublisherScheduler가 재발행하게 한다. */
    private void reopenFailedOutboxes(LocalDateTime updatedBefore, int limit) {
        for (BlockchainOutbox outbox :
                blockchainOutboxRepository.findStaleByStatus(
                        BlockchainOutboxStatus.FAILED, updatedBefore, limit)) {
            reopenOutbox(outbox, "FAILED outbox");
        }
    }

    /**
     * stale PENDING ledger를 처리한다.
     *
     * <p>txHash가 있으면 이미 submit됐지만 markSubmitted가 누락된 것 → receipt 직접 조회.
     * txHash가 없으면 outbox 발행 전/중에 프로세스가 죽은 것 → outbox를 재발행 대기로 되돌린다.
     */
    private void reconcilePendingLedgers(LocalDateTime updatedBefore, int limit) {
        for (BlockchainLedger ledger :
                blockchainLedgerRepository.findStaleByStatus(
                        BlockchainTxStatus.PENDING, updatedBefore, limit)) {
            if (hasText(ledger.getTxHash())) {
                reconcileReceipt(ledger);
                continue;
            }
            reopenOutboxByLedger(ledger, "PENDING ledger without txHash");
        }
    }

    /**
     * stale SUBMITTED ledger를 처리한다.
     *
     * <p>SUBMITTED = txHash는 커밋됐지만 receipt 수신 전에 프로세스가 죽은 상태.
     * txHash로 receipt를 재조회해 SUCCESS/FAILED로 수렴시킨다.
     * txHash가 없는 SUBMITTED는 데이터 이상 — 수동 확인이 필요하다.
     */
    private void reconcileSubmittedLedgers(LocalDateTime updatedBefore, int limit) {
        for (BlockchainLedger ledger :
                blockchainLedgerRepository.findStaleByStatus(
                        BlockchainTxStatus.SUBMITTED, updatedBefore, limit)) {
            if (!hasText(ledger.getTxHash())) {
                log.error(
                        "[reconcile] SUBMITTED ledger has no txHash. ledgerId={}, uuid={}",
                        ledger.getId(),
                        ledger.getIdempotentKey());
                continue;
            }
            reconcileReceipt(ledger);
        }
    }

    /**
     * txHash가 있는 stale FAILED ledger를 재확인한다.
     *
     * <p>receipt timeout 등 일시적 오류로 FAILED가 됐지만 체인에선 성공했을 수 있다.
     * txHash로 receipt를 다시 조회해 실제 체인 결과와 동기화한다.
     */
    private void reconcileFailedLedgersWithTxHash(LocalDateTime updatedBefore, int limit) {
        for (BlockchainLedger ledger :
                blockchainLedgerRepository.findStaleWithTxHashByStatus(
                        BlockchainTxStatus.FAILED, updatedBefore, limit)) {
            reconcileReceipt(ledger);
        }
    }

    /**
     * txHash로 Besu에서 receipt를 조회해 ledger 상태를 SUCCESS/FAILED로 확정한다.
     * Besu 연결 실패 등 일시적 오류는 로그만 남기고 다음 주기에 재시도한다.
     */
    private void reconcileReceipt(BlockchainLedger ledger) {
        try {
            TransactionReceipt receipt =
                    contractCallService.waitForReceiptByHash(ledger.getTxHash());
            if (receipt.isStatusOK()) {
                ledgerStateWriter.markSuccess(ledger.getId(), ledger.getIdempotentKey(), receipt);
            } else {
                ledgerStateWriter.markFailed(ledger.getId(), ledger.getIdempotentKey());
            }
            log.info(
                    "[reconcile] receipt reconciled. ledgerId={}, uuid={}, txHash={}",
                    ledger.getId(),
                    ledger.getIdempotentKey(),
                    ledger.getTxHash());
        } catch (BusinessException e) {
            log.warn(
                    "[reconcile] receipt 조회 실패. 다음 주기에 재시도. ledgerId={}, uuid={}, txHash={}, code={}",
                    ledger.getId(),
                    ledger.getIdempotentKey(),
                    ledger.getTxHash(),
                    e.getCode());
        }
    }

    /** ledgerId로 outbox를 찾아 재발행 대기 상태로 전환한다. outbox가 없으면 수동 확인이 필요한 데이터 이상이다. */
    private void reopenOutboxByLedger(BlockchainLedger ledger, String reason) {
        blockchainOutboxRepository
                .findByBlockchainLedgerId(ledger.getId())
                .ifPresentOrElse(
                        outbox -> reopenOutbox(outbox, reason),
                        () ->
                                log.error(
                                        "[reconcile] outbox 없음. 수동 확인 필요. ledgerId={}, uuid={}, reason={}",
                                        ledger.getId(),
                                        ledger.getIdempotentKey(),
                                        reason));
    }

    /** outbox를 NEW로 되돌려 OutboxPublisherScheduler가 다음 주기에 재발행하도록 한다. 이미 NEW이면 중복 처리이므로 건너뛴다. */
    private void reopenOutbox(BlockchainOutbox outbox, String reason) {
        if (outbox.getStatus() == BlockchainOutboxStatus.NEW) {
            return;
        }
        outbox.reopenForReconcile();
        blockchainOutboxRepository.save(outbox);
        log.info(
                "[reconcile] outbox 재발행 대기 전환. outboxId={}, ledgerId={}, uuid={}, reason={}",
                outbox.getId(),
                outbox.getBlockchainLedgerId(),
                outbox.getTransactionUuid(),
                reason);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
