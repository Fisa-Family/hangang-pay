package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.error.ChargeErrorCode;
import family.fisa.hangangpay.domain.transaction.code.error.PaymentErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeCalculateResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeLimitResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.dto.response.UserExchangeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.dto.response.UserPaymentHistoryDetail;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService {

    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("1000000");

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final PaginationService paginationService;
    private final BankClient bankClient;

    public CursorPageResponse<ChargeHistoryItem> getChargeHistories(
            Long userId, CursorPageRequest request, int size) {
        Long partyId =
                userRepository
                        .findPartyIdByUserId(userId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        ScrollPosition position = paginationService.resolveScrollPosition(request);
        Window<ChargeHistoryItem> window =
                transactionRepository.findChargeHistoriesByPartyId(
                        partyId, position, Limit.of(size));
        return paginationService.toCursorPage(window);
    }

    public CursorPageResponse<ExchangeHistoryItem> getExchangeHistories(
            Long userId, CursorPageRequest request, int size) {
        Long partyId =
                userRepository
                        .findPartyIdByUserId(userId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        ScrollPosition position = paginationService.resolveScrollPosition(request);
        Window<ExchangeHistoryItem> window =
                transactionRepository.findExchangeHistoriesByPartyId(
                        partyId, position, Limit.of(size));
        return paginationService.toCursorPage(window);
    }

    public CursorPageResponse<PaymentHistoryItem> getUserPaymentHistory(
            Long partyId, CursorPageRequest request, int size) {
        log.info("결제 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        Window<Transaction> window =
                transactionRepository.findPaymentHistory(
                        partyId, List.of(TransactionStatus.SUCCESS), Limit.of(size), position);

        List<Long> payeePartyIds =
                window.getContent().stream().map(t -> t.getToParty().getId()).toList();

        Map<Long, String> merchantNameMap =
                merchantRepository.findByParty_IdIn(payeePartyIds).stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.getParty().getId(), Merchant::getMerchantName));

        Window<PaymentHistoryItem> responseWindow =
                window.map(
                        t -> {
                            Long payeePartyId = t.getToParty().getId();
                            String merchantName = merchantNameMap.get(payeePartyId);
                            if (merchantName == null) {
                                log.warn(
                                        "가맹점 정보 없음. transactionId={}, payeePartyId={}",
                                        t.getId(),
                                        payeePartyId);
                                merchantName = "알 수 없는 가맹점";
                            }
                            return PaymentHistoryItem.from(t, merchantName);
                        });

        log.info("결제 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(responseWindow);
    }

    public UserChargeHistoryDetail getUserChargeHistoryDetail(Long partyId, Long transactionId) {
        Transaction transaction =
                findOwnedTransaction(partyId, transactionId, TransactionType.CHARGE);
        BlockchainLedgerResponse blockchainLedger = findBlockchainLedger(transaction);
        return UserChargeHistoryDetail.from(transaction, blockchainLedger);
    }

    public UserExchangeHistoryDetail getUserExchangeHistoryDetail(
            Long partyId, Long transactionId) {
        Transaction transaction =
                findOwnedTransaction(partyId, transactionId, TransactionType.EXCHANGE);
        BlockchainLedgerResponse blockchainLedger = findBlockchainLedger(transaction);
        return UserExchangeHistoryDetail.from(transaction, blockchainLedger);
    }

    public UserPaymentHistoryDetail getUserPaymentHistoryDetail(Long partyId, Long transactionId) {
        Transaction transaction =
                transactionRepository
                        .findByIdWithFromParty(transactionId)
                        .orElseThrow(
                                () -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        verifyOwner(partyId, transaction);
        BlockchainLedgerResponse blockchainLedger = findBlockchainLedger(transaction);
        return UserPaymentHistoryDetail.from(transaction, blockchainLedger);
    }

    private Transaction findOwnedTransaction(
            Long partyId, Long transactionId, TransactionType expectedType) {
        Transaction transaction =
                transactionRepository
                        .findByIdWithFromPartyAccountWallet(transactionId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.HISTORY_NOT_FOUND));
        if (transaction.getTransactionType() != expectedType) {
            throw new BusinessException(UserErrorCode.HISTORY_NOT_FOUND);
        }
        if (!transaction.getFromParty().getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
        return transaction;
    }

    private void verifyOwner(Long partyId, Transaction transaction) {
        if (!transaction.getFromParty().getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
    }

    private BlockchainLedgerResponse findBlockchainLedger(Transaction transaction) {
        if (transaction.getTxHash() == null) {
            return null;
        }
        try {
            return bankClient.getBlockchainLedgerByTxHash(transaction.getTxHash());
        } catch (Exception e) {
            log.warn("blockchain_ledger 조회 실패. txHash={}", transaction.getTxHash(), e);
            return null;
        }
    }

    public ChargeLimitResponse getChargeLimit(Long partyId) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);
        BigDecimal usedAmount =
                transactionRepository.sumMonthlyAmount(
                        partyId,
                        TransactionType.CHARGE,
                        TransactionStatus.SUCCESS,
                        startOfMonth,
                        startOfNextMonth);
        BigDecimal remainLimit = MONTHLY_LIMIT.subtract(usedAmount).max(BigDecimal.ZERO);
        return ChargeLimitResponse.of(
                MONTHLY_LIMIT, usedAmount, remainLimit, startOfNextMonth.toLocalDate().toString());
    }

    public ChargeCalculateResponse calculateCharge(Long partyId, BigDecimal chargeAmount) {
        if (chargeAmount.remainder(new BigDecimal("10000")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ChargeErrorCode.INVALID_UNIT);
        }
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);
        BigDecimal usedAmount =
                transactionRepository.sumMonthlyAmount(
                        partyId,
                        TransactionType.CHARGE,
                        TransactionStatus.SUCCESS,
                        startOfMonth,
                        startOfNextMonth);
        if (usedAmount.add(chargeAmount).compareTo(MONTHLY_LIMIT) > 0) {
            throw new BusinessException(ChargeErrorCode.LIMIT_EXCEEDED);
        }
        BigDecimal discountRate = new BigDecimal("0.10");
        BigDecimal discountAmount = chargeAmount.multiply(discountRate);
        BigDecimal actualPayAmount = chargeAmount.subtract(discountAmount);
        return ChargeCalculateResponse.builder()
                .chargeAmount(chargeAmount)
                .discountRate(discountRate)
                .discountAmount(discountAmount)
                .actualPayAmount(actualPayAmount)
                .build();
    }
}
