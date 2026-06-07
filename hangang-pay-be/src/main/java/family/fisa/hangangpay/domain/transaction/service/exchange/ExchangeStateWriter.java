package family.fisa.hangangpay.domain.transaction.service.exchange;

import family.fisa.hangangpay.client.bank.dto.ExchangeRequest;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * EXCHANGE 상태 전이를 짧은 트랜잭션 경계로 격리
 *
 * <p>외부 API 호출 (bank) 동안 DB 락 / 커넥션을 점유하지 않기 위해 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeStateWriter {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;

    /** 사용자 환전 슬롯 선점 — PRIMARY 계좌로 입금 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long claimExchange(Long partyId, ExchangeExecuteRequest request) {
        return claim(partyId, request, AccountType.PRIMARY);
    }

    /** 가맹점 환전 슬롯 선점 - SETTLEMENT 계좌로 입금 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long claimSettlementExchange(Long partyId, ExchangeExecuteRequest request) {
        return claim(partyId, request, AccountType.SETTLEMENT);
    }

    /** 환전 슬롯 선점 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long claim(Long partyId, ExchangeExecuteRequest request, AccountType depositType) {
        // 1. wallet 행 락 - 동일 사용자의 동시 환전 요청을 직렬화
        Wallet fromWallet =
                walletRepository
                        .findByParty_IdForUpdate(partyId)
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.WALLET_NOT_FOUND));

        // 2. single-flight 가드: 진행 중인 환전이 있으면 거절
        if (transactionRepository.existsInflightExchange(partyId)) {
            log.warn("환전 중복 요청 차단. partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
        }

        // 3. 입금대상: 계좌 찾아오기
        Account toAccount =
                accountRepository
                        .findByParty_IdAndAccountType(partyId, depositType)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // 4. PENDING transaction 저장
        Transaction transaction =
                transactionRepository.save(
                        Transaction.forExchange(
                                request.transactionUuid(),
                                fromWallet.getParty(),
                                fromWallet,
                                toAccount,
                                request.amount(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));

        return transaction.getId();
    }

    /** Bank 호출용 요청 객체 빌드 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ExchangeRequest buildBankRequest(Long transactionId, ExchangeExecuteRequest request) {
        Transaction tx =
                transactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.EXCHANGE_NOT_FOUND));

        return new ExchangeRequest(
                request.transactionUuid(),
                tx.getToAccount().getInstitution().getId(),
                tx.getFromWallet().getAddress(),
                tx.getToAccount().getAccountNumber(),
                request.amount());
    }

    /** 성공 마킹 + 응답 DTO 빌드 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeExecuteResponse completeExchange(
            Long transactionId, String txHash, String bankTransactionId) {

        Transaction tx =
                transactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.EXCHANGE_NOT_FOUND));

        tx.completeSuccessWithResponse(txHash, bankTransactionId);
        return ExchangeExecuteResponse.from(tx);
    }

    /** 실패 마킹 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failExchange(Long transactionId) {
        transactionRepository.findById(transactionId).ifPresent(Transaction::markFailed);
    }

    /** reconcile 시도 횟수 1 증가 후 새 값 반환 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementReconcileAttempt(Long transactionId) {
        Transaction tx =
                transactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.EXCHANGE_NOT_FOUND));

        tx.incrementReconcileAttempt();
        return tx.getReconcileAttemptCount();
    }
}
