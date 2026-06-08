package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
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
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
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
 * <p>payment/cancel은 오케스트레이터 역할만 담당한다. 메인 DB 트랜잭션은 PaymentExecutionService가 수행하고, 실패 ledger 저장은
 * rollback 이후 REQUIRES_NEW로 남긴다. charge는 기존 동기 방식을 유지한다.
 */
@Service
@RequiredArgsConstructor
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
    private final PaymentExecutionService paymentExecutionService;

    /** 충전: 계좌 → 토큰 mint. 동기 블록체인 호출을 유지한다. */
    @Transactional
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

    public PaymentResponse payment(PaymentRequest request) {
        try {
            // 결제 실행을 paymentExecutionService에게 위임
            return paymentExecutionService.payment(request);
        } catch (BusinessException e) {
            log.warn(
                    "[bank] payment 비즈니스 실패. transactionUuid={}, message={}",
                    request.transactionUuid(),
                    e.getMessage());
            // 실패 시 에러 반환 -> 이후 wallet ledger failed 기록
            paymentStateWriter.saveFailedWalletLedgersByAddress(
                    request.fromWalletAddress(),
                    request.toWalletAddress(),
                    request.transactionUuid(),
                    request.amount());
            throw e;
        }
    }

    public CancelResponse cancel(CancelRequest request) {
        try {
            return paymentExecutionService.cancel(request);
        } catch (BusinessException e) {
            log.warn(
                    "[bank] cancel 비즈니스 실패. transactionUuid={}, originalTransactionUuid={}, message={}",
                    request.transactionUuid(),
                    request.originalTransactionUuid(),
                    e.getMessage());
            paymentStateWriter.saveFailedWalletLedgersByAddress(
                    request.fromWalletAddress(),
                    request.toWalletAddress(),
                    request.transactionUuid(),
                    request.amount());
            throw e;
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

    private static void ensureSufficientBalance(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(
                    family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode
                            .TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }

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
