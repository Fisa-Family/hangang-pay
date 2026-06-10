package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.ExchangeBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerType;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.ExchangeSyncRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 환전의 메인 DB 트랜잭션을 수행 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ExchangeExecutionService {

    private final InstitutionRepository institutionRepository;
    private final BankWalletRepository bankWalletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final BlockchainSyncRequester syncRequester;

    public ExchangeResponse exchange(ExchangeRequest request) {
        log.info(
                "[bank] exchange 시작. transactionUuid={}, institutionId={}, amount={}",
                request.transactionUuid(),
                request.institutionId(),
                request.amount());

        // 1. 멱등성 확인
        Optional<AccountLedger> existingOpt =
                accountLedgerRepository.findByIdempotentKey(request.transactionUuid());
        if (existingOpt.isPresent()) {
            return handleIdempotent(request, existingOpt.get());
        }

        // 2. 기관/지갑/계좌 존재 검증
        findInstitution(request.institutionId());
        BankWallet bankWallet = findBankWallet(request.walletAddress());
        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());

        // 3. 토큰 잔액 검증
        ensureSufficientBalance(bankWallet.getBalance(), request.amount());

        // 4. 토큰 차감
        bankWallet.decreaseBalance(request.amount());

        // 5. 현금 입금
        bankAccount.increaseBalance(request.amount());
        BigDecimal newAccountBalance = bankAccount.getBalance();
        AccountLedger accountLedger =
                accountLedgerRepository.save(
                        AccountLedger.builder()
                                .bankAccount(bankAccount)
                                .ledgerType(LedgerType.DEPOSIT)
                                .status(LedgerStatus.SUCCESS)
                                .amount(request.amount())
                                .balanceAfter(newAccountBalance)
                                .idempotentKey(request.transactionUuid())
                                .build());

        // 6. blockchain_ledger(PENDING) + outbox(NEW) 생성
        ExchangeBlockchainPayload payload =
                new ExchangeBlockchainPayload(
                        request.institutionId(), bankWallet.getWalletAddress(), request.amount());
        syncRequester.request(new ExchangeSyncRequest(request.transactionUuid(), payload));

        log.info(
                "[bank] exchange 접수 완료(PROCESSING). transactionUuid={}", request.transactionUuid());
        return ExchangeResponse.accepted(request, accountLedger, newAccountBalance);
    }

    /** 동일 transactionUuid 재요청 처리. 결제 멱등 분기와 동일한 정책 */
    private ExchangeResponse handleIdempotent(ExchangeRequest request, AccountLedger existing) {
        switch (existing.getStatus()) {
            case SUCCESS -> {
                log.info(
                        "[bank] 멱등성: 환전 SUCCESS 재요청. transactionUuid={}",
                        request.transactionUuid());

                BankAccount bankAccount =
                        findBankAccount(request.institutionId(), request.accountNumber());

                return ExchangeResponse.from(
                        request.transactionUuid(), existing, bankAccount.getBalance());
            }

            // PENDING = 아직 처리중
            case PENDING ->
                    throw new BusinessException(
                            TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
            // FAILED = 이미 실패한 거래 재요청
            default -> throw new BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_FAILED);
        }
    }

    private void findInstitution(Long institutionId) {
        institutionRepository
                .findById(institutionId)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));
    }

    private BankWallet findBankWallet(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddress(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private BankAccount findBankAccount(Long institutionId, String accountNumber) {
        return bankAccountRepository
                .findByInstitution_IdAndAccountNumber(institutionId, accountNumber)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_ACCOUNT_NOT_FOUND));
    }

    private AccountLedger findAccountLedger(String transactionUuid) {
        return accountLedgerRepository
                .findByIdempotentKey(transactionUuid)
                .orElseThrow(
                        () -> new BusinessException(TransactionErrorCode.TRANSACTION_NOT_FOUND));
    }

    /** 토큰 잔액 검증 — DB 잔액(서비스 단위)과 요청 금액을 직접 비교 (결제 ensureSufficientBalance와 동일). */
    private static void ensureSufficientBalance(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }
}
