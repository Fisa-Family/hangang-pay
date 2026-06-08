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
    public Transaction claimExchange(Long partyId, ExchangeExecuteRequest request) {
        return buildExchange(partyId, request, AccountType.PRIMARY);
    }

    /** 가맹점 환전 슬롯 선점 - SETTLEMENT 계좌로 입금 */
    public Transaction claimSettlementExchange(Long partyId, ExchangeExecuteRequest request) {
        return buildExchange(partyId, request, AccountType.SETTLEMENT);
    }

    /** 환전 거래 생성 선점 */
    public Transaction buildExchange(
            Long partyId, ExchangeExecuteRequest request, AccountType depositType) {
        // 1. wallet 조회
        Wallet fromWallet =
                walletRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.WALLET_NOT_FOUND));

        // 2. 입금대상: 계좌 찾아오기
        Account toAccount =
                accountRepository
                        .findByParty_IdAndAccountType(partyId, depositType)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // 3. 거래 엔티티 생성
        return Transaction.forExchange(
                request.transactionUuid(),
                fromWallet.getParty(),
                fromWallet,
                toAccount,
                request.amount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    /** Bank 호출용 요청 객체 빌드 */
    public ExchangeRequest buildBankRequest(Transaction tx, ExchangeExecuteRequest request) {
        return new ExchangeRequest(
                request.transactionUuid(),
                tx.getToAccount().getInstitution().getId(),
                tx.getFromWallet().getAddress(),
                tx.getToAccount().getAccountNumber(),
                request.amount());
    }

    /** 미저장 거래를 SUCCESS로 확정하며 저장 + 응답 빌드 */
    public ExchangeExecuteResponse completeExchange(
            Transaction tx, String txHash, String bankTransactionId) {

        tx.completeWithBankResponse(txHash, bankTransactionId);
        Transaction saved = transactionRepository.save(tx);
        return ExchangeExecuteResponse.from(saved);
    }

    /** id로 로드한 UNKNOWN 거래를 SUCCESS로 확정 + 응답 빌드 */
    @Transactional
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

    /** 실패 마킹. bank에 거래가 없을 때 UNKNOWN -> FAILED 확정 */
    @Transactional
    public void failExchange(Long transactionId) {
        transactionRepository.findById(transactionId).ifPresent(Transaction::markFailed);
    }

    /** 응답 불확실 -> UNKNOWN으로 저장 */
    @Transactional
    public ExchangeExecuteResponse markUnknownExchange(Transaction tx) {
        tx.markUnknown();
        Transaction saved = transactionRepository.save(tx);
        return ExchangeExecuteResponse.from(saved);
    }

    /** reconcile 시도 횟수 1 증가 후 새 값 반환 */
    @Transactional
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
