package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.CancelBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.PaymentBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerType;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * 결제/취소/충전 트랜잭션을 처리하는 커맨드 서비스.
 *
 * <p>payment/cancel은 DB-First 방식으로 동작한다. 요청 스레드에서는 DB 잔액만 처리하고, 블록체인 동기화는 Outbox → RabbitMQ →
 * Consumer 파이프라인으로 위임한다. charge는 기존 동기 방식을 유지한다.
 *
 * <p>트랜잭션 경계: 메인 @Transactional 안에서 비관적 락으로 지갑을 조회하고, WalletLedger 저장은 REQUIRES_NEW로 분리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TransactionCommandService {

    /** ERC20 기본 decimals (1e18) — BigDecimal 금액을 컨트랙트 단위로 변환할 때 사용 */
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    private final BankAccountRepository bankAccountRepository;
    private final BankWalletRepository bankWalletRepository;
    private final InstitutionRepository institutionRepository;
    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final ContractCallService contractCallService;
    private final PaymentStateWriter paymentStateWriter;
    private final BlockchainSyncRequester syncRequester;

    /** 충전: 계좌 → 토큰 mint. 동기 블록체인 호출을 유지한다. */
    public ChargeResponse charge(ChargeRequest request) {
        log.info(
                "[bank] charge 시작. transactionUuid={}, institutionId={}, amount={}",
                request.transactionUuid(),
                request.institutionId(),
                request.amount());

        // 1. 소속 기관 조회
        Institution institution = findInstitution(request.institutionId());

        // 2. 출금 계좌 조회 + 잔액 검증
        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());
        ensureSufficientBalance(bankAccount.getBalance(), request.amount());

        // 3. 입금 지갑 조회
        BankWallet bankWallet = findBankWallet(request.walletAddress());

        // 4. 계좌 잔액 차감
        BigDecimal newAccountBalance = bankAccount.getBalance().subtract(request.amount());
        bankAccount.updateBalance(newAccountBalance);

        // 5. account_ledger WITHDRAWAL 기록
        AccountLedger savedAccountLedger =
                accountLedgerRepository.save(
                        AccountLedger.builder()
                                .bankAccount(bankAccount)
                                .ledgerType(LedgerType.WITHDRAWAL)
                                .status(LedgerStatus.SUCCESS)
                                .amount(request.amount())
                                .balanceAfter(newAccountBalance)
                                .idempotentKey(request.transactionUuid())
                                .build());

        // 6. 블록체인 mint 호출
        TransactionReceipt receipt =
                contractCallService.charge(
                        request.institutionId(),
                        bankWallet.getWalletAddress(),
                        toTokenUnit(request.mintAmount()));

        // 7. blockchain_ledger 기록
        BlockchainLedger ledger =
                saveBlockchainLedger(institution, receipt, request.transactionUuid());

        // 8. 지갑 잔액은 컨트랙트 balanceOf 기준으로 조회한다.
        BigDecimal walletBalance =
                fromTokenUnit(contractCallService.getBalance(bankWallet.getWalletAddress()));

        // 9. Response 반환
        return new ChargeResponse(
                request.transactionUuid(),
                savedAccountLedger.getId(),
                ledger.getTxHash(),
                ledger.getBlockNumber(),
                ledger.getConfirmedAt(),
                walletBalance);
    }

    /**
     * 결제: DB-First 방식으로 지갑 잔액을 차감/증가하고, 블록체인 동기화는 Outbox로 위임한다.
     *
     * <p>isMerchant()는 요청 스레드에서 검증한다. 컨트랙트 view call이므로 상태 변경 없이 안전하다.
     */
    public PaymentResponse payment(PaymentRequest request) {
        log.info(
                "[bank] payment 시작. transactionUuid={}, amount={}",
                request.transactionUuid(),
                request.amount());

        // 1. 비관적 락으로 지갑 조회 — 동시 요청에 의한 잔액 이중 차감 방지
        BankWallet fromWallet = findBankWalletWithLock(request.fromWalletAddress());
        BankWallet toWallet = findBankWalletWithLock(request.toWalletAddress());

        // 2. 멱등성 확인 — 이미 처리된 transactionUuid이면 기존 결과 반환
        return paymentStateWriter
                .findExisting(request.transactionUuid())
                .map(
                        existing -> {
                            switch (existing.getStatus()) {
                                case SUCCESS -> {
                                    log.info(
                                            "[bank] 멱등성: SUCCESS 재요청. transactionUuid={}",
                                            request.transactionUuid());
                                    // 확정 시각과 현재 DB 잔액으로 응답 재구성
                                    return PaymentResponse.from(
                                            request.transactionUuid(),
                                            WalletLedgerStatus.SUCCESS,
                                            existing.getConfirmedAt(),
                                            fromWallet.getBalance(),
                                            toWallet.getBalance());
                                }
                                case PENDING -> {
                                    paymentStateWriter.throwDuplicateProcessing(
                                            request.transactionUuid());
                                    return null;
                                }
                                default -> {
                                    paymentStateWriter.throwAlreadyFailed(
                                            request.transactionUuid());
                                    return null;
                                }
                            }
                        })
                .orElseGet(
                        () -> {
                            try {
                                // 3. 가맹점 검증 — toWallet이 가맹점 화이트리스트에 등록되어 있어야 한다
                                ensureMerchant(toWallet.getWalletAddress());

                                // 4. DB 잔액 검증
                                ensureSufficientBalance(fromWallet.getBalance(), request.amount());

                                // 5. DB 잔액 업데이트 (dirty-checking으로 커밋 시 반영)
                                fromWallet.updateBalance(
                                        fromWallet.getBalance().subtract(request.amount()));
                                toWallet.updateBalance(toWallet.getBalance().add(request.amount()));

                                // 6. WalletLedger SUCCESS 저장
                                LocalDateTime confirmedAt =
                                        paymentStateWriter.saveSuccessWalletLedgers(
                                                fromWallet,
                                                toWallet,
                                                request.transactionUuid(),
                                                request.amount());

                                // 7. 블록체인 비동기 동기화 요청 — BlockchainLedger(PENDING) + Outbox(NEW) 생성
                                syncRequester.request(
                                        new PaymentSyncRequest(
                                                request.transactionUuid(),
                                                new PaymentBlockchainPayload(
                                                        fromWallet.getWalletAddress(),
                                                        toWallet.getWalletAddress(),
                                                        request.amount())));

                                log.info(
                                        "[bank] payment 완료. transactionUuid={}",
                                        request.transactionUuid());
                                return PaymentResponse.from(
                                        request.transactionUuid(),
                                        WalletLedgerStatus.SUCCESS,
                                        confirmedAt,
                                        fromWallet.getBalance(),
                                        toWallet.getBalance());

                            } catch (BusinessException e) {
                                // 8. 비즈니스 예외 발생 시 WalletLedger FAILED 저장 후 재던짐
                                //    REQUIRES_NEW로 커밋되어 메인 트랜잭션 롤백과 무관하게 실패 기록이 남는다.
                                log.warn(
                                        "[bank] payment 비즈니스 실패. transactionUuid={}, message={}",
                                        request.transactionUuid(),
                                        e.getMessage());
                                paymentStateWriter.saveFailedWalletLedgers(
                                        fromWallet, toWallet, request.transactionUuid(), request.amount());
                                throw e;
                            } catch (RuntimeException e) {
                                log.error(
                                        "[bank] payment 시스템 예외. transactionUuid={}",
                                        request.transactionUuid(),
                                        e);
                                throw e;
                            }
                        });
    }

    /**
     * 결제 취소: DB-First 방식으로 가맹점→사용자 방향으로 잔액을 복원하고, 블록체인 동기화는 Outbox로 위임한다.
     *
     * <p>cancel에서 fromWallet은 가맹점(환불 출금), toWallet은 사용자(환불 입금)이다.
     */
    public CancelResponse cancel(CancelRequest request) {
        log.info(
                "[bank] cancel 시작. transactionUuid={}, originalTransactionUuid={}",
                request.transactionUuid(),
                request.originalTransactionUuid());

        // 1. 비관적 락으로 지갑 조회
        BankWallet fromWallet = findBankWalletWithLock(request.fromWalletAddress());
        BankWallet toWallet = findBankWalletWithLock(request.toWalletAddress());

        // 2. 멱등성 확인
        return paymentStateWriter
                .findExisting(request.transactionUuid())
                .map(
                        existing -> {
                            switch (existing.getStatus()) {
                                case SUCCESS -> {
                                    log.info(
                                            "[bank] 멱등성: 취소 SUCCESS 재요청. transactionUuid={}",
                                            request.transactionUuid());
                                    return CancelResponse.from(
                                            request.transactionUuid(),
                                            request.originalTransactionUuid(),
                                            WalletLedgerStatus.SUCCESS,
                                            existing.getConfirmedAt(),
                                            fromWallet.getBalance(),
                                            toWallet.getBalance());
                                }
                                case PENDING -> {
                                    paymentStateWriter.throwDuplicateProcessing(
                                            request.transactionUuid());
                                    return null;
                                }
                                default -> {
                                    paymentStateWriter.throwAlreadyFailed(
                                            request.transactionUuid());
                                    return null;
                                }
                            }
                        })
                .orElseGet(
                        () -> {
                            try {
                                // 3. 가맹점 검증 — fromWallet(환불 출금 지갑)이 가맹점이어야 한다
                                ensureMerchant(fromWallet.getWalletAddress());

                                // 4. DB 잔액 검증
                                ensureSufficientBalance(fromWallet.getBalance(), request.amount());

                                // 5. DB 잔액 업데이트
                                fromWallet.updateBalance(
                                        fromWallet.getBalance().subtract(request.amount()));
                                toWallet.updateBalance(toWallet.getBalance().add(request.amount()));

                                // 6. WalletLedger SUCCESS 저장
                                LocalDateTime confirmedAt =
                                        paymentStateWriter.saveSuccessWalletLedgers(
                                                fromWallet,
                                                toWallet,
                                                request.transactionUuid(),
                                                request.amount());

                                // 7. 블록체인 비동기 동기화 요청
                                syncRequester.request(
                                        new CancelSyncRequest(
                                                request.transactionUuid(),
                                                new CancelBlockchainPayload(
                                                        request.originalTransactionUuid(),
                                                        fromWallet.getWalletAddress(),
                                                        toWallet.getWalletAddress(),
                                                        request.amount())));

                                log.info(
                                        "[bank] cancel 완료. transactionUuid={}",
                                        request.transactionUuid());
                                return CancelResponse.from(
                                        request.transactionUuid(),
                                        request.originalTransactionUuid(),
                                        WalletLedgerStatus.SUCCESS,
                                        confirmedAt,
                                        fromWallet.getBalance(),
                                        toWallet.getBalance());

                            } catch (BusinessException e) {
                                // 8. 비즈니스 예외 발생 시 WalletLedger FAILED 저장 후 재던짐
                                log.warn(
                                        "[bank] cancel 비즈니스 실패. transactionUuid={}, originalTransactionUuid={}, message={}",
                                        request.transactionUuid(),
                                        request.originalTransactionUuid(),
                                        e.getMessage());
                                paymentStateWriter.saveFailedWalletLedgers(
                                        fromWallet, toWallet, request.transactionUuid(), request.amount());
                                throw e;
                            } catch (RuntimeException e) {
                                log.error(
                                        "[bank] cancel 시스템 예외. transactionUuid={}, originalTransactionUuid={}",
                                        request.transactionUuid(),
                                        request.originalTransactionUuid(),
                                        e);
                                throw e;
                            }
                        });
    }

    // ── 내부 BlockchainSyncRequest 구현체 ─────────────────────────────────────

    /** PAYMENT 타입 Outbox 요청 */
    private record PaymentSyncRequest(String transactionUuid, PaymentBlockchainPayload data)
            implements BlockchainSyncRequest {
        public BlockchainSyncType type() {
            return BlockchainSyncType.PAYMENT;
        }

        public Object payload() {
            return data;
        }
    }

    /** CANCEL 타입 Outbox 요청 */
    private record CancelSyncRequest(String transactionUuid, CancelBlockchainPayload data)
            implements BlockchainSyncRequest {
        public BlockchainSyncType type() {
            return BlockchainSyncType.CANCEL;
        }

        public Object payload() {
            return data;
        }
    }

    // ── 공통 헬퍼 ─────────────────────────────────────────────────────────────

    private Institution findInstitution(Long institutionId) {
        return institutionRepository
                .findById(institutionId)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));
    }

    private BankAccount findBankAccount(Long institutionId, String accountNumber) {
        return bankAccountRepository
                .findByInstitution_IdAndAccountNumber(institutionId, accountNumber)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_ACCOUNT_NOT_FOUND));
    }

    private BankWallet findBankWallet(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddress(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_WALLET_NOT_FOUND));
    }

    /** 비관적 락으로 지갑을 조회한다. 동시 결제 요청에 의한 잔액 이중 차감을 방지한다. */
    private BankWallet findBankWalletWithLock(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddressWithLock(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private static void ensureSufficientBalance(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    /**
     * 온체인 가맹점 화이트리스트를 검증한다.
     *
     * <p>view call이므로 상태 변경 없이 안전하다. 화이트리스트 등록은 결제 요청 수락 시점에 검증하고, 비동기 컨슈머에서는 FATAL 처리한다.
     */
    private void ensureMerchant(String walletAddress) {
        if (!contractCallService.isMerchant(walletAddress)) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        }
    }

    /** wallet_address 정규화: 0x prefix + 소문자 */
    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }

    /** BigDecimal 금액을 컨트랙트 단위(1e18 wei)로 변환 */
    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }

    private static BigDecimal fromTokenUnit(BigInteger amount) {
        return new BigDecimal(amount).divide(new BigDecimal(TOKEN_DECIMALS));
    }

    /** TransactionReceipt에서 핵심 정보를 추출해 blockchain_ledger row 저장 (charge 전용) */
    private BlockchainLedger saveBlockchainLedger(
            Institution institution, TransactionReceipt receipt, String idempotentKey) {
        Long blockNumber =
                receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValueExact() : null;
        return blockchainLedgerRepository.save(
                BlockchainLedger.builder()
                        .institution(institution)
                        .txHash(receipt.getTransactionHash())
                        .blockNumber(blockNumber)
                        .status(BlockchainTxStatus.SUCCESS)
                        .confirmedAt(LocalDateTime.now())
                        .idempotentKey(idempotentKey)
                        .build());
    }
}
