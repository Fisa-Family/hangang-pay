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
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeStateWriter {

    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    private final InstitutionRepository institutionRepository;
    private final BankWalletRepository bankWalletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final ContractCallService contractCallService;

    /** PENDING BlockchainLedger 선 저장 + 사전 검증. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long claimExchange(ExchangeRequest request) {
        Institution institution = findInstitution(request.institutionId());
        BankWallet bankWallet = findBankWallet(request.walletAddress());
        ensureSufficientTokenBalance(
                contractCallService.getBalance(bankWallet.getWalletAddress()),
                toTokenUnit(request.amount()));
        findBankAccount(request.institutionId(), request.accountNumber());

        BlockchainLedger ledger =
                blockchainLedgerRepository.save(
                        BlockchainLedger.of(
                                institution,
                                BlockchainTxStatus.PENDING,
                                request.transactionUuid()));

        return ledger.getId();
    }

    /** 컨트렉트 성공: SUCCESS 마킹 + 잔액 반영 + AccountLedger SUCCESS */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeResponse completeExchange(
            Long ledgerId, ExchangeRequest request, TransactionReceipt receipt) {
        BlockchainLedger ledger =
                blockchainLedgerRepository
                        .findById(ledgerId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.BLOCKCHAIN_LEDGER_NOT_FOUND));

        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());
        ledger.markSuccess(receipt);

        BigDecimal newAccountBalance = bankAccount.getBalance().add(request.amount());
        bankAccount.updateBalance(newAccountBalance);

        AccountLedger savedAccountLedger =
                accountLedgerRepository.save(
                        AccountLedger.builder()
                                .bankAccount(bankAccount)
                                .ledgerType(LedgerType.DEPOSIT)
                                .amount(request.amount())
                                .balanceAfter(newAccountBalance)
                                .status(LedgerStatus.SUCCESS)
                                .idempotentKey(request.transactionUuid())
                                .build());

        return ExchangeResponse.of(request, savedAccountLedger, ledger, newAccountBalance);
    }

    /** 컨트렉트 실패: FAILED 마킹 + AccountLedger DEPOSIT FAILED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failExchange(Long ledgerId, ExchangeRequest request) {
        blockchainLedgerRepository
                .findById(ledgerId)
                .ifPresentOrElse(
                        BlockchainLedger::markFailed,
                        () ->
                                log.warn(
                                        "[bank] FAILED 마킹 대상 BlockchainLedger 없음. ledgerId={}",
                                        ledgerId));

        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());

        accountLedgerRepository.save(
                AccountLedger.builder()
                        .bankAccount(bankAccount)
                        .ledgerType(LedgerType.DEPOSIT)
                        .amount(request.amount())
                        .balanceAfter(bankAccount.getBalance())
                        .status(LedgerStatus.FAILED)
                        .idempotentKey(request.transactionUuid())
                        .build());
    }

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

    private static void ensureSufficientTokenBalance(
            BigInteger tokenBalance, BigInteger tokenAmount) {
        if (tokenBalance.compareTo(tokenAmount) < 0) {
            throw new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    private static BigInteger toTokenUnit(BigDecimal amount) {
        // ERC20 decimals=18 기준으로 서비스 금액을 컨트랙트 최소 단위로 변환한다.
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }
}
