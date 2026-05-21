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
import family.fisa.hangangpay.domain.transaction.dto.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeReceiptResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeReceiptResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
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

    public ChargeReceiptResponse charge(Long partyId, ChargeExecuteRequest request) {
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

        try {
            ChargeResponse bankResponse =
                    bankClient.charge(
                            new ChargeRequest(
                                    request.transactionUuid(),
                                    account.getInstitution().getId(),
                                    account.getAccountNumber(),
                                    wallet.getAddress(),
                                    request.amount()));
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

        return ChargeReceiptResponse.from(saved, null);
    }

    public ExchangeReceiptResponse exchange(Long partyId, ExchangeExecuteRequest request) {
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

        try {
            ExchangeResponse bankResponse =
                    bankClient.exchange(
                            new ExchangeRequest(
                                    request.transactionUuid(),
                                    account.getInstitution().getId(),
                                    wallet.getAddress(),
                                    account.getAccountNumber(),
                                    request.amount()));
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

        return ExchangeReceiptResponse.from(saved, null);
    }

    public PaymentResponse payment(Long partyId, PaymentExecuteRequest request) {
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

        // TODO: APV-YYYY-NNNNNNNN 패턴(transaction.id 8자리 zero padding)으로 채번 후 갱신
        String tempApprovalNumber = "TEMP-" + request.transactionUuid().substring(0, 8);

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

        try {
            family.fisa.hangangpay.client.bank.dto.PaymentResponse bankResponse =
                    bankClient.payment(
                            new PaymentRequest(
                                    request.transactionUuid(),
                                    fromWallet.getAddress(),
                                    toWallet.getAddress(),
                                    request.amount()));
            saved.completeWithBankResponse(bankResponse.txHash(), null);
        } catch (BusinessException e) {
            saved.markFailed();
            throw e;
        } catch (Exception e) {
            log.error("PAYMENT bank 호출 실패. transactionUuid={}", request.transactionUuid(), e);
            saved.markFailed();
            throw new BusinessException(GeneralErrorCode.BANK_CALL_FAILED);
        }

        return PaymentResponse.from(saved, null);
    }

    public PaymentCancelResponse cancelPayment(Long partyId, PaymentCancelRequest request) {
        Transaction original =
                transactionRepository
                        .findByTransactionUuid(request.originalTransactionUuid())
                        .orElseThrow(
                                () -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

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

        try {
            CancelResponse bankResponse =
                    bankClient.cancel(
                            new CancelRequest(
                                    request.transactionUuid(),
                                    request.originalTransactionUuid(),
                                    original.getToWallet().getAddress(),
                                    original.getFromWallet().getAddress(),
                                    original.getAmount()));
            saved.completeWithBankResponse(bankResponse.txHash(), null);
        } catch (BusinessException e) {
            saved.markFailed();
            throw e;
        } catch (Exception e) {
            log.error("CANCEL bank 호출 실패. transactionUuid={}", request.transactionUuid(), e);
            saved.markFailed();
            throw new BusinessException(GeneralErrorCode.BANK_CALL_FAILED);
        }

        return PaymentCancelResponse.from(saved, null);
    }
}
