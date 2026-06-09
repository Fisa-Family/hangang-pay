package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerType;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 환전 실패 보상 기록 전용 writer. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeStateWriter {

    private final BankAccountRepository bankAccountRepository;
    private final AccountLedgerRepository accountLedgerRepository;

    /** 환전 실패 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedAccountLedger(ExchangeRequest request) {
        if (accountLedgerRepository.findByIdempotentKey(request.transactionUuid()).isPresent()) {
            log.info(
                    "[bank] account_ledger 기록 존재 — FAILED 보상 생략. transactionUuid={}",
                    request.transactionUuid());
            return;
        }

        // 1. 환전 실패 저장
        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());
        accountLedgerRepository.save(
                AccountLedger.builder()
                        .bankAccount(bankAccount)
                        .ledgerType(LedgerType.DEPOSIT)
                        .status(LedgerStatus.FAILED)
                        .amount(request.amount())
                        .balanceAfter(bankAccount.getBalance())
                        .idempotentKey(request.transactionUuid())
                        .build());

        log.info(
                "[bank] account_ledger FAILED 저장 완료. transactionUuid={}",
                request.transactionUuid());
    }

    private BankAccount findBankAccount(Long institutionId, String accountNumber) {
        return bankAccountRepository
                .findByInstitution_IdAndAccountNumber(institutionId, accountNumber)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_ACCOUNT_NOT_FOUND));
    }
}
