package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentStateWriter {

    private final BlockchainLedgerRepository blockchainLedgerRepository;

    /** transactionUuid 기준으로 기존 ledger를 조회한다. 결과에 따라 멱등성 분기(재사용/중복오류/실패오류)를 호출자가 처리한다. */
    public Optional<BlockchainLedger> findExisting(String transactionUuid) {
        return blockchainLedgerRepository.findByIdempotentKey(transactionUuid);
    }

    /**
     * 컨트랙트 호출 전 PENDING 상태의 blockchain_ledger 저장. REQUIRES_NEW: 부모 트랜잭션과 분리해 즉시 커밋 → 동시 중복 요청이
     * PENDING을 감지할 수 있게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BlockchainLedger preparePending(String transactionUuid, Institution institution) {
        log.info("[bank] PENDING ledger 저장. transactionUuid={}", transactionUuid);
        return blockchainLedgerRepository.save(
                BlockchainLedger.builder()
                        .idempotentKey(transactionUuid)
                        .institution(institution)
                        .status(BlockchainTxStatus.PENDING)
                        .build());
    }

    /**
     * 컨트랙트 성공 후 CONFIRMED 상태로 전환하고 PaymentResponse를 반환한다. ID로 재조회해 managed entity를 얻어야
     * dirty-checking이 DB에 반영된다.
     *
     * @param ledgerId preparePending()이 반환한 ledger의 ID
     * @param receipt 컨트랙트 receipt
     * @param fromBalance 차감 후 송신 지갑 잔액
     * @param toBalance 증가 후 수신 지갑 잔액
     */
    public PaymentResponse completePayment(
            Long ledgerId,
            TransactionReceipt receipt,
            String transactionUuid,
            BigDecimal fromBalance,
            BigDecimal toBalance) {

        // 1. ID로 재조회해 현재 트랜잭션에서 managed 상태로 만들기
        BlockchainLedger managed = fetchById(ledgerId, transactionUuid);

        // 2. ledger CONFIRMED 전환 (dirty-checking으로 commit 시 DB 반영)
        managed.markSuccess(receipt);
        log.info(
                "[bank] CONFIRMED 전환 완료. transactionUuid={}, txHash={}",
                transactionUuid,
                managed.getTxHash());

        // 3. PaymentResponse 반환
        return PaymentResponse.from(managed, transactionUuid, fromBalance, toBalance);
    }

    /**
     * 컨트랙트 성공 후 CONFIRMED 상태로 전환하고 CancelResponse를 반환한다. ID로 재조회해 managed entity를 얻어야
     * dirty-checking이 DB에 반영된다.
     *
     * @param ledgerId preparePending()이 반환한 ledger의 ID
     * @param receipt 컨트랙트 receipt
     * @param transactionUuid 취소 거래 UUID
     * @param originalTransactionUuid 원본 결제 거래 UUID
     * @param fromBalance 차감 후 송신 지갑 잔액
     * @param toBalance 증가 후 수신 지갑 잔액
     */
    public CancelResponse completeCancel(
            Long ledgerId,
            TransactionReceipt receipt,
            String transactionUuid,
            String originalTransactionUuid,
            BigDecimal fromBalance,
            BigDecimal toBalance) {

        // 1. ID로 재조회해 현재 트랜잭션에서 managed 상태로 만들기
        BlockchainLedger managed = fetchById(ledgerId, transactionUuid);

        // 2. ledger CONFIRMED 전환
        managed.markSuccess(receipt);
        log.info(
                "[bank] 취소 CONFIRMED 전환 완료. transactionUuid={}, txHash={}",
                transactionUuid,
                managed.getTxHash());

        // 3. CancelResponse 반환
        return CancelResponse.from(
                managed, transactionUuid, originalTransactionUuid, fromBalance, toBalance);
    }

    /**
     * 컨트랙트 실패 후 FAILED 상태로 전환. REQUIRES_NEW: 부모 트랜잭션이 롤백되더라도 FAILED 상태가 DB에 커밋되도록 한다. ID로 재조회해 이 새
     * 트랜잭션에서 managed entity를 얻는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long ledgerId, String transactionUuid) {
        // 1. REQUIRES_NEW 컨텍스트에서 managed entity 재조회
        BlockchainLedger managed = fetchById(ledgerId, transactionUuid);

        // 2. FAILED 전환 (이 트랜잭션 커밋 시 DB 반영)
        managed.fail();
        log.warn("[bank] FAILED 전환. transactionUuid={}", transactionUuid);
    }

    /**
     * CONFIRMED ledger를 기반으로 PaymentResponse를 재구성한다. 멱등성 재요청 시 컨트랙트를 재호출하지 않고 기존 결과를 반환할 때 사용한다.
     */
    public PaymentResponse buildResponseFromConfirmed(
            BlockchainLedger ledger,
            String transactionUuid,
            BigDecimal fromBalance,
            BigDecimal toBalance) {
        return PaymentResponse.from(ledger, transactionUuid, fromBalance, toBalance);
    }

    /**
     * CONFIRMED cancel ledger를 기반으로 CancelResponse를 재구성한다. 멱등성 재요청 시 컨트랙트를 재호출하지 않고 기존 결과를 반환할 때
     * 사용한다.
     */
    public CancelResponse buildCancelResponseFromConfirmed(
            BlockchainLedger ledger,
            String transactionUuid,
            String originalTransactionUuid,
            BigDecimal fromBalance,
            BigDecimal toBalance) {
        return CancelResponse.from(
                ledger, transactionUuid, originalTransactionUuid, fromBalance, toBalance);
    }

    /** ID로 blockchain_ledger를 조회해 managed entity를 반환한다 */
    private BlockchainLedger fetchById(Long ledgerId, String transactionUuid) {
        return blockchainLedgerRepository
                .findById(ledgerId)
                .orElseThrow(
                        () -> {
                            log.error(
                                    "[bank] ledger 재조회 실패. ledgerId={}, transactionUuid={}",
                                    ledgerId,
                                    transactionUuid);
                            return new NoSuchElementException(
                                    "BlockchainLedger not found: id=" + ledgerId);
                        });
    }

    /** PENDING 중복 요청 에러 */
    public void throwDuplicateProcessing(String transactionUuid) {
        log.warn("[bank] 중복 처리 요청 감지. transactionUuid={}", transactionUuid);
        throw new BusinessException(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
    }

    /** FAILED 거래 재시도 에러 */
    public void throwAlreadyFailed(String transactionUuid) {
        log.warn("[bank] 이미 실패한 거래 재요청. transactionUuid={}", transactionUuid);
        throw new BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_FAILED);
    }
}
