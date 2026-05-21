package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.CancelRequest;
import family.fisa.hangangpay.client.bank.dto.CancelResponse;
import family.fisa.hangangpay.client.bank.dto.ChargeRequest;
import family.fisa.hangangpay.client.bank.dto.ChargeResponse;
import family.fisa.hangangpay.client.bank.dto.ExchangeRequest;
import family.fisa.hangangpay.client.bank.dto.ExchangeResponse;
import family.fisa.hangangpay.client.bank.dto.PaymentRequest;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.error.PaymentErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.dto.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeReceiptResponse;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeReceiptResponse;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TransactionCommandService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final PartyRepository partyRepository;
    private final BankClient bankClient;

    /** CHARGE 실행 — 은행 계좌 출금 + 토큰 mint */
    public ChargeReceiptResponse charge(Long partyId, ChargeExecuteRequest request) {
        log.info("충전 실행 시작. partyId={}, transactionUuid={}", partyId, request.transactionUuid());

        // 1. 소유 계좌 + 지갑 조회
        Account account =
                accountRepository
                        .findByIdAndParty_Id(request.accountId(), partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        Wallet wallet =
                walletRepository
                        .findByIdAndParty_Id(request.walletId(), partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_OWNER));
        Party party =
                partyRepository
                        .findById(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // 2. Transaction Entity 생성 + PENDING 저장
        Transaction transaction =
                Transaction.forCharge(
                        request.transactionUuid(),
                        party,
                        account,
                        wallet,
                        request.amount(),
                        request.discountAmount(),
                        request.discountRate());
        Transaction saved = transactionRepository.save(transaction);

        // 3. BankClient 호출 (동기 - bank가 mint 처리 후 응답)
        try {
            ChargeResponse bankResponse =
                    bankClient.charge(
                            new ChargeRequest(
                                    request.transactionUuid(),
                                    account.getInstitution().getId(),
                                    account.getAccountNumber(),
                                    wallet.getAddress(),
                                    request.amount()));

            // 4. 응답 반영 — bankTransactionId는 Long, Entity 필드는 String 이므로 변환
            String bankTxId =
                    bankResponse.bankTransactionId() != null
                            ? String.valueOf(bankResponse.bankTransactionId())
                            : null;
            saved.completeWithBankResponse(bankResponse.txHash(), bankTxId);
        } catch (BusinessException e) {
            saved.markFailed();
            throw e;
        } catch (Exception e) {
            log.error("CHARGE bank 호출 실패. transactionUuid={}", request.transactionUuid(), e);
            saved.markFailed();
            throw new BusinessException(GeneralErrorCode.BANK_CALL_FAILED);
        }

        log.info("충전 실행 완료. transactionId={}, txHash={}", saved.getId(), saved.getTxHash());
        return ChargeReceiptResponse.from(saved, null);
    }

    /** EXCHANGE 실행 — 토큰 burn + 은행 계좌 입금 */
    public ExchangeReceiptResponse exchange(Long partyId, ExchangeExecuteRequest request) {
        log.info("환전 실행 시작. partyId={}, transactionUuid={}", partyId, request.transactionUuid());

        // 1. 소유 지갑 + 계좌 조회
        Wallet wallet =
                walletRepository
                        .findByIdAndParty_Id(request.walletId(), partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_OWNER));
        Account account =
                accountRepository
                        .findByIdAndParty_Id(request.accountId(), partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        Party party =
                partyRepository
                        .findById(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // 2. Transaction Entity 생성 + PENDING 저장
        Transaction transaction =
                Transaction.forExchange(
                        request.transactionUuid(),
                        party,
                        wallet,
                        account,
                        request.amount(),
                        request.discountAmount(),
                        request.discountRate());
        Transaction saved = transactionRepository.save(transaction);

        // 3. BankClient 호출 (동기)
        try {
            ExchangeResponse bankResponse =
                    bankClient.exchange(
                            new ExchangeRequest(
                                    request.transactionUuid(),
                                    account.getInstitution().getId(),
                                    wallet.getAddress(),
                                    account.getAccountNumber(),
                                    request.amount()));

            // 4. 응답 반영
            String bankTxId =
                    bankResponse.bankTransactionId() != null
                            ? String.valueOf(bankResponse.bankTransactionId())
                            : null;
            saved.completeWithBankResponse(bankResponse.txHash(), bankTxId);
        } catch (BusinessException e) {
            saved.markFailed();
            throw e;
        } catch (Exception e) {
            log.error("EXCHANGE bank 호출 실패. transactionUuid={}", request.transactionUuid(), e);
            saved.markFailed();
            throw new BusinessException(GeneralErrorCode.BANK_CALL_FAILED);
        }

        log.info("환전 실행 완료. transactionId={}, txHash={}", saved.getId(), saved.getTxHash());
        return ExchangeReceiptResponse.from(saved, null);
    }

    /** PAYMENT 실행 — 지갑 → 지갑 transfer */
    public PaymentResponse payment(Long partyId, PaymentExecuteRequest request) {
        log.info("결제 실행 시작. partyId={}, transactionUuid={}", partyId, request.transactionUuid());

        // 1. 송신 지갑 (소유자 검증) + 수신 지갑 조회
        //    수신 지갑은 가맹점 지갑이므로 소유자 검증 안 함 (TODO: party_type=MERCHANT 검증)
        Wallet fromWallet =
                walletRepository
                        .findByIdAndParty_Id(request.fromWalletId(), partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_OWNER));
        Wallet toWallet =
                walletRepository
                        .findById(request.toWalletId())
                        .orElseThrow(
                                () -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));
        Party fromParty =
                partyRepository
                        .findById(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        Party toParty = toWallet.getParty();

        // 2. 임시 approvalNumber로 PENDING 저장 (Transaction.id 채번 전이라 정식 패턴 적용 불가)
        //    TODO: APV-YYYY-NNNNNNNN 패턴(transaction.id 8자리 zero padding)으로 채번 후 갱신
        String tempApprovalNumber = "TEMP-" + request.transactionUuid().substring(0, 8);

        // 3. Transaction Entity 생성 + 저장
        Transaction transaction =
                Transaction.forPayment(
                        request.transactionUuid(),
                        fromParty,
                        toParty,
                        fromWallet,
                        toWallet,
                        request.amount(),
                        tempApprovalNumber,
                        request.itemName());
        Transaction saved = transactionRepository.save(transaction);

        // 4. BankClient 호출
        try {
            family.fisa.hangangpay.client.bank.dto.PaymentResponse bankResponse =
                    bankClient.payment(
                            new PaymentRequest(
                                    request.transactionUuid(),
                                    fromWallet.getAddress(),
                                    toWallet.getAddress(),
                                    request.amount()));

            // 5. 응답 반영 (PaymentResponse에는 bankTransactionId 없음 — 결제는 account_ledger 거치지 않음)
            saved.completeWithBankResponse(bankResponse.txHash(), null);
        } catch (BusinessException e) {
            saved.markFailed();
            throw e;
        } catch (Exception e) {
            log.error("PAYMENT bank 호출 실패. transactionUuid={}", request.transactionUuid(), e);
            saved.markFailed();
            throw new BusinessException(GeneralErrorCode.BANK_CALL_FAILED);
        }

        log.info("결제 실행 완료. transactionId={}, txHash={}", saved.getId(), saved.getTxHash());
        return PaymentResponse.from(saved, null);
    }

    /** PAYMENT CANCEL 실행 — 원본 PAYMENT의 역방향 transfer */
    public PaymentCancelResponse cancelPayment(Long partyId, PaymentCancelRequest request) {
        log.info(
                "결제 취소 실행 시작. partyId={}, transactionUuid={}, originalTransactionUuid={}",
                partyId,
                request.transactionUuid(),
                request.originalTransactionUuid());

        // 1. 원본 PAYMENT 조회 (역방향 wallets 추출용)
        //    TODO: 원본 transaction_type=PAYMENT 검증, 취소 가능 여부 검증 등은 향후 작업
        Transaction original =
                transactionRepository
                        .findByTransactionUuid(request.originalTransactionUuid())
                        .orElseThrow(
                                () -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // 2. CANCEL Transaction 생성 - 원본의 from/to를 뒤집어서 저장
        //    cancel-sender(원본 수취자 = 가맹점)가 cancel-receiver(원본 송신자 = 사용자)에게 환불
        Transaction cancelTransaction =
                Transaction.forCancel(
                        request.transactionUuid(),
                        request.originalTransactionUuid(),
                        original.getToParty(),
                        original.getFromParty(),
                        original.getToWallet(),
                        original.getFromWallet(),
                        original.getAmount(),
                        original.getApprovalNumber());
        Transaction saved = transactionRepository.save(cancelTransaction);

        // 3. BankClient 호출 (역방향 transfer)
        try {
            CancelResponse bankResponse =
                    bankClient.cancel(
                            new CancelRequest(
                                    request.transactionUuid(),
                                    request.originalTransactionUuid(),
                                    original.getToWallet().getAddress(),
                                    original.getFromWallet().getAddress(),
                                    original.getAmount()));

            // 4. 응답 반영
            saved.completeWithBankResponse(bankResponse.txHash(), null);
        } catch (BusinessException e) {
            saved.markFailed();
            throw e;
        } catch (Exception e) {
            log.error("CANCEL bank 호출 실패. transactionUuid={}", request.transactionUuid(), e);
            saved.markFailed();
            throw new BusinessException(GeneralErrorCode.BANK_CALL_FAILED);
        }

        log.info("결제 취소 실행 완료. transactionId={}, txHash={}", saved.getId(), saved.getTxHash());
        return PaymentCancelResponse.from(saved, null);
    }
}
