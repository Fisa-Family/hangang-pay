package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerDirection;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.ledger.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제/취소 흐름의 상태 전환을 담당하는 서비스.
 *
 * <p>WalletLedger(SUCCESS)는 부모 트랜잭션에 참여해 잔액 변경/outbox 생성과 함께 커밋한다. WalletLedger(FAILED)만
 * REQUIRES_NEW로 분리해 메인 트랜잭션이 롤백돼도 실패 기록이 남게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentStateWriter {

    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final BankWalletRepository bankWalletRepository;
    private final WalletLedgerRepository walletLedgerRepository;

    /** transactionUuid 기준으로 기존 ledger를 조회한다. 결과에 따라 멱등성 분기를 호출자가 처리한다. */
    public Optional<BlockchainLedger> findExisting(String transactionUuid) {
        return blockchainLedgerRepository.findByIdempotentKey(transactionUuid);
    }

    /** 결제/취소 성공 WalletLedger를 부모 트랜잭션 안에서 저장한다. */
    public LocalDateTime saveSuccessWalletLedgers(
            BankWallet fromWallet, BankWallet toWallet, String transactionUuid, BigDecimal amount) {
        LocalDateTime confirmedAt = LocalDateTime.now();
        saveWalletLedgerPair(
                fromWallet,
                toWallet,
                transactionUuid,
                amount,
                WalletLedgerStatus.SUCCESS,
                confirmedAt);
        log.info("[payment] WalletLedger SUCCESS 저장 완료. uuid={}", transactionUuid);
        return confirmedAt;
    }

    /** 결제/취소 실패 WalletLedger를 독립 트랜잭션으로 저장한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedWalletLedgers(
            BankWallet fromWallet, BankWallet toWallet, String transactionUuid, BigDecimal amount) {
        saveWalletLedgerPair(
                fromWallet, toWallet, transactionUuid, amount, WalletLedgerStatus.FAILED, null);
        log.info("[payment] WalletLedger FAILED 저장 완료. uuid={}", transactionUuid);
    }

    /** 결제/취소 실패 WalletLedger를 주소 기반으로 재조회한 뒤 독립 트랜잭션에 저장한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedWalletLedgersByAddress(
            String fromWalletAddress,
            String toWalletAddress,
            String transactionUuid,
            BigDecimal amount) {
        BankWallet fromWallet = fetchBankWallet(fromWalletAddress);
        BankWallet toWallet = fetchBankWallet(toWalletAddress);
        saveWalletLedgerPair(
                fromWallet, toWallet, transactionUuid, amount, WalletLedgerStatus.FAILED, null);
        log.info("[payment] WalletLedger FAILED 저장 완료. uuid={}", transactionUuid);
    }

    /**
     * 컨트랙트 호출 전 PENDING 상태의 blockchain_ledger 저장.
     *
     * <p>REQUIRES_NEW: 동시 중복 요청이 PENDING을 감지할 수 있도록 즉시 커밋한다.
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
     * 컨트랙트 실패 후 blockchain_ledger를 FAILED 상태로 전환한다.
     *
     * <p>REQUIRES_NEW: 부모 트랜잭션이 롤백되더라도 FAILED 상태가 DB에 커밋되도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long ledgerId, String transactionUuid) {
        // 1. REQUIRES_NEW 컨텍스트에서 managed entity 재조회
        BlockchainLedger managed = fetchById(ledgerId, transactionUuid);

        // 2. FAILED 전환
        managed.fail();
        log.warn("[bank] FAILED 전환. transactionUuid={}", transactionUuid);
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

    private void saveWalletLedgerPair(
            BankWallet fromWallet,
            BankWallet toWallet,
            String transactionUuid,
            BigDecimal amount,
            WalletLedgerStatus status,
            LocalDateTime confirmedAt) {
        walletLedgerRepository.save(
                WalletLedger.builder()
                        .transactionUuid(transactionUuid)
                        .bankWallet(fromWallet)
                        .direction(WalletLedgerDirection.DEBIT)
                        .status(status)
                        .amount(amount)
                        .confirmedAt(confirmedAt)
                        .build());

        walletLedgerRepository.save(
                WalletLedger.builder()
                        .transactionUuid(transactionUuid)
                        .bankWallet(toWallet)
                        .direction(WalletLedgerDirection.CREDIT)
                        .status(status)
                        .amount(amount)
                        .confirmedAt(confirmedAt)
                        .build());
    }

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

    private BankWallet fetchBankWallet(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddress(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }
}
