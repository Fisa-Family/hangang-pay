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
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerType;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
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

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TransactionCommandService {

    /**
     * ERC20 기본 decimals (1e18) - BigDecimal 금액을 컨트랙트 단위로 변환할 때 사용
     */
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    private final BankAccountRepository bankAccountRepository;
    private final BankWalletRepository bankWalletRepository;
    private final InstitutionRepository institutionRepository;
    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final ContractCallService contractCallService;

    /**
     * 충전: 계좌 → 토큰 mint
     */
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

        // 5. account_ledger WITHDRAWAL 기록 (저장 후 채번된 id를 응답에 사용)
        AccountLedger savedAccountLedger =
            accountLedgerRepository.save(
                AccountLedger.builder()
                             .bankAccount(bankAccount)
                             .ledgerType(LedgerType.WITHDRAWAL)
                             .amount(request.amount())
                             .balanceAfter(newAccountBalance)
                             .build());

        // 6. 블록체인 mint 호출 (계좌 → 사용자 지갑으로 토큰 발행)
        TransactionReceipt receipt =
            contractCallService.charge(
                request.institutionId(),
                bankWallet.getWalletAddress(),
                toTokenUnit(request.amount()));

        // 7. blockchain_ledger 기록
        BlockchainLedger ledger = saveBlockchainLedger(institution, receipt);

        // 8. 지갑 잔액 증가
        BigDecimal newWalletBalance = bankWallet.getBalance().add(request.amount());
        bankWallet.updateBalance(newWalletBalance);

        // 9. Response 반환
        return new ChargeResponse(
            request.transactionUuid(),
            savedAccountLedger.getId(),
            ledger.getTxHash(),
            ledger.getBlockNumber(),
            ledger.getConfirmedAt(),
            newWalletBalance);
    }

    /**
     * 환전: 토큰 burn → 계좌 입금
     */
    public ExchangeResponse exchange(ExchangeRequest request) {
        log.info(
            "[bank] exchange 시작. transactionUuid={}, institutionId={}, amount={}",
            request.transactionUuid(),
            request.institutionId(),
            request.amount());

        // 1. 소속 기관 조회
        Institution institution = findInstitution(request.institutionId());

        // 2. 출금 지갑 조회 + 잔액 검증
        BankWallet bankWallet = findBankWallet(request.walletAddress());
        ensureSufficientBalance(bankWallet.getBalance(), request.amount());

        // 3. 입금 계좌 조회
        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());

        // 4. 지갑 잔액 차감
        BigDecimal newWalletBalance = bankWallet.getBalance().subtract(request.amount());
        bankWallet.updateBalance(newWalletBalance);

        // 5. 블록체인 burn(refund) 호출 (토큰 소각 → 기관별 reserve 환급)
        TransactionReceipt receipt =
            contractCallService.refund(
                request.institutionId(),
                bankWallet.getWalletAddress(),
                toTokenUnit(request.amount()));

        // 6. blockchain_ledger 기록 (idempotent_key = BE의 transactionUuid)
        BlockchainLedger ledger =
            saveBlockchainLedger(institution, receipt, request.transactionUuid());

        // 7. 계좌 잔액 증가
        BigDecimal newAccountBalance = bankAccount.getBalance().add(request.amount());
        bankAccount.updateBalance(newAccountBalance);

        // 8. account_ledger DEPOSIT 기록 (idempotent_key = BE의 transactionUuid)
        AccountLedger savedAccountLedger =
            accountLedgerRepository.save(
                AccountLedger.builder()
                             .bankAccount(bankAccount)
                             .ledgerType(LedgerType.DEPOSIT)
                             .amount(request.amount())
                             .balanceAfter(newAccountBalance)
                             .idempotentKey(request.transactionUuid())
                             .build());

        log.info(
            "[bank] exchange 완료. transactionUuid={}, txHash={}, accountLedgerId={}",
            request.transactionUuid(),
            ledger.getTxHash(),
            savedAccountLedger.getId());

        // 9. Response 반환
        return new ExchangeResponse(
            request.transactionUuid(),
            savedAccountLedger.getId(),
            ledger.getTxHash(),
            ledger.getBlockNumber(),
            ledger.getConfirmedAt(),
            newAccountBalance);
    }

    /**
     * 결제: 지갑 → 지갑 transfer
     */
    public PaymentResponse payment(PaymentRequest request) {
        // 1. 송신/수신 지갑 조회
        BankWallet fromWallet = findBankWallet(request.fromWalletAddress());
        BankWallet toWallet = findBankWallet(request.toWalletAddress());
        ensureSufficientBalance(fromWallet.getBalance(), request.amount());

        // 2. 송신 지갑 잔액 차감
        BigDecimal newFromBalance = fromWallet.getBalance().subtract(request.amount());
        fromWallet.updateBalance(newFromBalance);

        // 3. 블록체인 transfer 호출
        TransactionReceipt receipt =
            contractCallService.pay(
                fromWallet.getWalletAddress(),
                toWallet.getWalletAddress(),
                toTokenUnit(request.amount()));

        // 4. blockchain_ledger 기록 (송신 지갑 소속 기관 기준)
        BlockchainLedger ledger = saveBlockchainLedger(fromWallet.getInstitution(), receipt);

        // 5. 수신 지갑 잔액 증가
        BigDecimal newToBalance = toWallet.getBalance().add(request.amount());
        toWallet.updateBalance(newToBalance);

        // 6. Response 반환
        return new PaymentResponse(
            request.transactionUuid(),
            ledger.getTxHash(),
            ledger.getBlockNumber(),
            ledger.getConfirmedAt(),
            newFromBalance,
            newToBalance);
    }

    /**
     * 결제 취소: PAYMENT 역방향 transfer
     */
    public CancelResponse cancel(CancelRequest request) {
        // 1. 송신/수신 지갑 조회 (BE에서 이미 역방향으로 들어옴)
        BankWallet fromWallet = findBankWallet(request.fromWalletAddress());
        BankWallet toWallet = findBankWallet(request.toWalletAddress());
        ensureSufficientBalance(fromWallet.getBalance(), request.amount());

        // 2. 송신 지갑 잔액 차감
        BigDecimal newFromBalance = fromWallet.getBalance().subtract(request.amount());
        fromWallet.updateBalance(newFromBalance);

        // 3. 블록체인 cancelPayment 호출
        TransactionReceipt receipt =
            contractCallService.cancelPayment(
                fromWallet.getWalletAddress(),
                toWallet.getWalletAddress(),
                toTokenUnit(request.amount()));

        // 4. blockchain_ledger 기록 (새 row, 원본과 동일한 transactionUuid는 BE/응답에서만 관리)
        BlockchainLedger ledger = saveBlockchainLedger(fromWallet.getInstitution(), receipt);

        // 5. 수신 지갑 잔액 증가
        BigDecimal newToBalance = toWallet.getBalance().add(request.amount());
        toWallet.updateBalance(newToBalance);

        // 6. Response 반환
        return new CancelResponse(
            request.transactionUuid(),
            request.originalTransactionUuid(),
            ledger.getTxHash(),
            ledger.getBlockNumber(),
            ledger.getConfirmedAt(),
            newFromBalance,
            newToBalance);
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

    private static void ensureSufficientBalance(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    /**
     * wallet_address 정규화: 0x prefix + 소문자
     */
    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }

    /**
     * BigDecimal 금액을 컨트랙트 단위(1e18 wei)로 변환
     */
    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }

    /**
     * TransactionReceipt에서 핵심 정보를 추출해 blockchain_ledger row 저장
     */
    private BlockchainLedger saveBlockchainLedger(
        Institution institution, TransactionReceipt receipt) {
        Long blockNumber =
            receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValueExact() : null;
        return blockchainLedgerRepository.save(
            BlockchainLedger.builder()
                            .institution(institution)
                            .txHash(receipt.getTransactionHash())
                            .blockNumber(blockNumber)
                            .status(BlockchainTxStatus.CONFIRMED)
                            .confirmedAt(LocalDateTime.now())
                            .build());
    }

    private BlockchainLedger saveBlockchainLedger(
        Institution institution, TransactionReceipt receipt, String idempotentKey) {
        Long blockNumber =
            receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValueExact() : null;
        return blockchainLedgerRepository.save(
            BlockchainLedger.builder()
                            .institution(institution)
                            .txHash(receipt.getTransactionHash())
                            .blockNumber(blockNumber)
                            .status(BlockchainTxStatus.CONFIRMED)
                            .confirmedAt(LocalDateTime.now())
                            .idempotentKey(idempotentKey)
                            .build());
    }
}
