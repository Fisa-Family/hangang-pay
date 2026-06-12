package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.CancelBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.PaymentBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제/취소의 메인 DB 트랜잭션을 수행한다.
 *
 * <p>비관적 락과 잔액 변경, 성공 ledger 저장, outbox 생성까지만 담당한다. 실패 ledger 저장은 바깥 오케스트레이터가 메인 트랜잭션 rollback 이후
 * 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentExecutionService {

    private final BankWalletRepository bankWalletRepository;
    private final PaymentStateWriter paymentStateWriter;
    private final BlockchainSyncRequester syncRequester;
    private final family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService
            contractCallService;

    public PaymentResponse payment(PaymentRequest request) {
        log.info(
                "[bank] payment 시작. transactionUuid={}, amount={}",
                request.transactionUuid(),
                request.amount());

        // 1. 멱등성 확인 - DB lock 이전
        Optional<WalletLedger> existingOpt =
                paymentStateWriter.findExisting(request.transactionUuid());
        if (existingOpt.isPresent()) {
            WalletLedger existing = existingOpt.get();
            switch (existing.getStatus()) {
                case SUCCESS -> {
                    log.info(
                            "[bank] 멱등성: SUCCESS 재요청. transactionUuid={}",
                            request.transactionUuid());
                    BankWallet fromWallet = findBankWallet(request.fromWalletAddress());
                    BankWallet toWallet = findBankWallet(request.toWalletAddress());
                    return PaymentResponse.from(
                            request.transactionUuid(),
                            WalletLedgerStatus.SUCCESS,
                            existing.getConfirmedAt(),
                            fromWallet.getBalance(),
                            toWallet.getBalance());
                }
                case PENDING ->
                        paymentStateWriter.throwDuplicateProcessing(request.transactionUuid());
                default -> paymentStateWriter.throwAlreadyFailed(request.transactionUuid());
            }
        }

        // 2. merchant whitelist 확인
        ensureMerchant(normalizeAddress(request.toWalletAddress()));

        // 3. wallet lock 걸고 잔액 이체
        BankWallet fromWallet = findBankWalletWithLock(request.fromWalletAddress());
        BankWallet toWallet = findBankWalletWithLock(request.toWalletAddress());

        ensureSufficientBalance(fromWallet.getBalance(), request.amount());

        fromWallet.updateBalance(fromWallet.getBalance().subtract(request.amount()));
        toWallet.updateBalance(toWallet.getBalance().add(request.amount()));

        // 4. wallet ledger에 success 기록 - 같은 트랜잭션 내에서 수행
        LocalDateTime confirmedAt =
                paymentStateWriter.saveSuccessWalletLedgers(
                        fromWallet, toWallet, request.transactionUuid(), request.amount());

        // 5. blockchain 비동기 요청
        syncRequester.request(
                new PaymentSyncRequest(
                        request.transactionUuid(),
                        new PaymentBlockchainPayload(
                                fromWallet.getWalletAddress(),
                                toWallet.getWalletAddress(),
                                request.amount())));

        log.info("[bank] payment 완료. transactionUuid={}", request.transactionUuid());
        return PaymentResponse.from(
                request.transactionUuid(),
                WalletLedgerStatus.SUCCESS,
                confirmedAt,
                fromWallet.getBalance(),
                toWallet.getBalance());
    }

    public CancelResponse cancel(CancelRequest request) {
        log.info(
                "[bank] cancel 시작. transactionUuid={}, originalTransactionUuid={}",
                request.transactionUuid(),
                request.originalTransactionUuid());

        Optional<WalletLedger> existingOpt =
                paymentStateWriter.findExisting(request.transactionUuid());
        if (existingOpt.isPresent()) {
            WalletLedger existing = existingOpt.get();
            switch (existing.getStatus()) {
                case SUCCESS -> {
                    log.info(
                            "[bank] 멱등성: 취소 SUCCESS 재요청. transactionUuid={}",
                            request.transactionUuid());
                    BankWallet fromWallet = findBankWallet(request.fromWalletAddress());
                    BankWallet toWallet = findBankWallet(request.toWalletAddress());
                    return CancelResponse.from(
                            request.transactionUuid(),
                            request.originalTransactionUuid(),
                            WalletLedgerStatus.SUCCESS,
                            existing.getConfirmedAt(),
                            fromWallet.getBalance(),
                            toWallet.getBalance());
                }
                case PENDING ->
                        paymentStateWriter.throwDuplicateProcessing(request.transactionUuid());
                default -> paymentStateWriter.throwAlreadyFailed(request.transactionUuid());
            }
        }

        ensureMerchant(normalizeAddress(request.fromWalletAddress()));

        // cancel의 fromWallet은 merchant, toWallet은 user이므로 to -> from 순서로 잠근다.
        BankWallet toWallet = findBankWalletWithLock(request.toWalletAddress());
        BankWallet fromWallet = findBankWalletWithLock(request.fromWalletAddress());

        ensureSufficientBalance(fromWallet.getBalance(), request.amount());

        fromWallet.updateBalance(fromWallet.getBalance().subtract(request.amount()));
        toWallet.updateBalance(toWallet.getBalance().add(request.amount()));

        LocalDateTime confirmedAt =
                paymentStateWriter.saveSuccessWalletLedgers(
                        fromWallet, toWallet, request.transactionUuid(), request.amount());

        syncRequester.request(
                new CancelSyncRequest(
                        request.transactionUuid(),
                        new CancelBlockchainPayload(
                                request.originalTransactionUuid(),
                                fromWallet.getWalletAddress(),
                                toWallet.getWalletAddress(),
                                request.amount())));

        log.info("[bank] cancel 완료. transactionUuid={}", request.transactionUuid());
        return CancelResponse.from(
                request.transactionUuid(),
                request.originalTransactionUuid(),
                WalletLedgerStatus.SUCCESS,
                confirmedAt,
                fromWallet.getBalance(),
                toWallet.getBalance());
    }

    private BankWallet findBankWallet(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddress(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private BankWallet findBankWalletWithLock(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddressWithLock(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private static void ensureSufficientBalance(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    private void ensureMerchant(String walletAddress) {
        if (!contractCallService.isMerchant(walletAddress)) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        }
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }

    private record PaymentSyncRequest(String transactionUuid, PaymentBlockchainPayload data)
            implements BlockchainSyncRequest {
        public BlockchainSyncType type() {
            return BlockchainSyncType.PAYMENT;
        }

        public String orderingKey() {
            return data.fromWalletAddress();
        }

        public Object payload() {
            return data;
        }
    }

    private record CancelSyncRequest(String transactionUuid, CancelBlockchainPayload data)
            implements BlockchainSyncRequest {
        public BlockchainSyncType type() {
            return BlockchainSyncType.CANCEL;
        }

        public String orderingKey() {
            return data.toWalletAddress();
        }

        public Object payload() {
            return data;
        }
    }
}
