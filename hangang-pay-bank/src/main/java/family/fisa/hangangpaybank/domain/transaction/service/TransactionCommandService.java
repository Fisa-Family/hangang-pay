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

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TransactionCommandService {

    /** ERC20 기본 decimals (1e18) - BigDecimal 금액을 컨트랙트 단위로 변환할 때 사용 */
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    private final BankAccountRepository bankAccountRepository;
    private final BankWalletRepository bankWalletRepository;
    private final InstitutionRepository institutionRepository;
    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final ContractCallService contractCallService;
    private final PaymentStateWriter paymentStateWriter;

    /** 충전: 계좌 → 토큰 mint */
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
                                .status(LedgerStatus.SUCCESS)
                                .amount(request.amount())
                                .balanceAfter(newAccountBalance)
                                .idempotentKey(request.transactionUuid())
                                .build());

        // 6. 블록체인 mint 호출 (충전가 mint, 컨트랙트에서 실제로는 amount 단위로 mint하지만, BE에서는 할인율 적용된 finalAmount 단위로
        // 멱등성 판단)
        TransactionReceipt receipt =
                contractCallService.charge(
                        request.institutionId(),
                        bankWallet.getWalletAddress(),
                        toTokenUnit(request.mintAmount()));

        // 7. blockchain_ledger 기록 (idempotent_key = BE의 transactionUuid)
        BlockchainLedger ledger =
                saveBlockchainLedger(institution, receipt, request.transactionUuid());

        // 8. 지갑 잔액 증가 (충전가)
        BigDecimal newWalletBalance = bankWallet.getBalance().add(request.mintAmount());
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

    /** 결제: 지갑 → 지갑 transfer */
    public PaymentResponse payment(PaymentRequest request) {
        log.info(
                "[bank] payment 시작. transactionUuid={}, amount={}",
                request.transactionUuid(),
                request.amount());

        // 1. 송신/수신 지갑 조회
        BankWallet fromWallet = findBankWallet(request.fromWalletAddress());
        BankWallet toWallet = findBankWallet(request.toWalletAddress());

        // 2. 멱등성 확인 (기존 ledger가 있으면 분기 처리)
        return paymentStateWriter
                .findExisting(request.transactionUuid())
                .map(
                        existing -> {
                            switch (existing.getStatus()) {
                                case SUCCESS -> {
                                    // 이미 완료된 거래 → 기존 결과를 그대로 반환 (컨트랙트 재호출 없음)
                                    log.info(
                                            "[bank] 멱등성: CONFIRMED 재요청 감지. transactionUuid={}",
                                            request.transactionUuid());
                                    return paymentStateWriter.buildResponseFromConfirmed(
                                            existing,
                                            request.transactionUuid(),
                                            fromWallet.getBalance(),
                                            toWallet.getBalance());
                                }
                                case PENDING -> {
                                    // 처리 중인 거래 → 중복 요청 에러
                                    paymentStateWriter.throwDuplicateProcessing(
                                            request.transactionUuid());
                                    return null; // throwDuplicateProcessing이 예외를 던지므로 도달 불가
                                }
                                default -> {
                                    // FAILED → 재시도 불가 에러
                                    paymentStateWriter.throwAlreadyFailed(
                                            request.transactionUuid());
                                    return null; // throwAlreadyFailed가 예외를 던지므로 도달 불가
                                }
                            }
                        })
                .orElseGet(
                        () -> {
                            // 3. 잔액 검증 및 송신 지갑 차감
                            ensureSufficientBalance(fromWallet.getBalance(), request.amount());
                            BigDecimal newFromBalance =
                                    fromWallet.getBalance().subtract(request.amount());
                            fromWallet.updateBalance(newFromBalance);

                            // 4. PENDING blockchain_ledger 먼저 저장 (멱등성 키 확보)
                            BlockchainLedger ledger =
                                    paymentStateWriter.preparePending(
                                            request.transactionUuid(), fromWallet.getInstitution());

                            // 5. 블록체인 transfer 호출
                            try {
                                TransactionReceipt receipt =
                                        contractCallService.pay(
                                                fromWallet.getWalletAddress(),
                                                toWallet.getWalletAddress(),
                                                toTokenUnit(request.amount()));

                                // 6. 수신 지갑 잔액 증가
                                BigDecimal newToBalance =
                                        toWallet.getBalance().add(request.amount());
                                toWallet.updateBalance(newToBalance);

                                // 7. CONFIRMED 전환 후 응답 반환
                                return paymentStateWriter.completePayment(
                                        ledger.getId(),
                                        receipt,
                                        request.transactionUuid(),
                                        newFromBalance,
                                        newToBalance);

                            } catch (Exception e) {
                                // 8. 컨트랙트 실패 → FAILED 전환
                                paymentStateWriter.markFailed(
                                        ledger.getId(), request.transactionUuid());
                                throw e;
                            }
                        });
    }

    /** 결제 취소: PAYMENT 역방향 transfer */
    public CancelResponse cancel(CancelRequest request) {
        log.info(
                "[bank] cancel 시작. transactionUuid={}, originalTransactionUuid={}",
                request.transactionUuid(),
                request.originalTransactionUuid());

        // 1. 송신/수신 지갑 조회 (BE에서 이미 역방향으로 들어옴)
        BankWallet fromWallet = findBankWallet(request.fromWalletAddress());
        BankWallet toWallet = findBankWallet(request.toWalletAddress());

        // 2. 멱등성 확인 (기존 ledger가 있으면 분기 처리)
        return paymentStateWriter
                .findExisting(request.transactionUuid())
                .map(
                        existing -> {
                            switch (existing.getStatus()) {
                                case SUCCESS -> {
                                    // 이미 완료된 취소 → 기존 결과를 그대로 반환
                                    log.info(
                                            "[bank] 멱등성: 취소 CONFIRMED 재요청 감지. transactionUuid={}",
                                            request.transactionUuid());
                                    return paymentStateWriter.buildCancelResponseFromConfirmed(
                                            existing,
                                            request.transactionUuid(),
                                            request.originalTransactionUuid(),
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
                            // 3. 잔액 검증 및 송신 지갑 차감
                            ensureSufficientBalance(fromWallet.getBalance(), request.amount());
                            BigDecimal newFromBalance =
                                    fromWallet.getBalance().subtract(request.amount());
                            fromWallet.updateBalance(newFromBalance);

                            // 4. PENDING blockchain_ledger 먼저 저장 (멱등성 키 확보)
                            BlockchainLedger ledger =
                                    paymentStateWriter.preparePending(
                                            request.transactionUuid(), fromWallet.getInstitution());

                            // 5. 블록체인 cancelPayment 호출
                            try {
                                TransactionReceipt receipt =
                                        contractCallService.cancelPayment(
                                                fromWallet.getWalletAddress(),
                                                toWallet.getWalletAddress(),
                                                toTokenUnit(request.amount()));

                                // 6. 수신 지갑 잔액 증가
                                BigDecimal newToBalance =
                                        toWallet.getBalance().add(request.amount());
                                toWallet.updateBalance(newToBalance);

                                // 7. CONFIRMED 전환 후 응답 반환
                                return paymentStateWriter.completeCancel(
                                        ledger.getId(),
                                        receipt,
                                        request.transactionUuid(),
                                        request.originalTransactionUuid(),
                                        newFromBalance,
                                        newToBalance);

                            } catch (Exception e) {
                                // 8. 컨트랙트 실패 → FAILED 전환
                                paymentStateWriter.markFailed(
                                        ledger.getId(), request.transactionUuid());
                                throw e;
                            }
                        });
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

    /** TransactionReceipt에서 핵심 정보를 추출해 blockchain_ledger row 저장 */
    private BlockchainLedger saveBlockchainLedger(
            Institution institution, TransactionReceipt receipt) {
        Long blockNumber =
                receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValueExact() : null;
        return blockchainLedgerRepository.save(
                BlockchainLedger.builder()
                        .institution(institution)
                        .txHash(receipt.getTransactionHash())
                        .blockNumber(blockNumber)
                        .status(BlockchainTxStatus.SUCCESS)
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
                        .status(BlockchainTxStatus.SUCCESS)
                        .confirmedAt(LocalDateTime.now())
                        .idempotentKey(idempotentKey)
                        .build());
    }
}
