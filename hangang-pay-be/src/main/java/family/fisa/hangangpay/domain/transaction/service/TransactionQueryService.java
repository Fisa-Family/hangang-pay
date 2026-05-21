package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.error.PaymentErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.UserPaymentHistoryDetail;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.code.error.ChargeErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeCalculateResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeLimitResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.dto.response.UserExchangeHistoryDetail;
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

    /** Transaction 내부 CHARGE 타입 내역 조회 */
    public CursorPageResponse<ChargeHistoryItem> getChargeHistories(
            Long userId, CursorPageRequest request, int size) {

        // 1. userId partyId 변환
        Long partyId =
                userRepository
                        .findPartyIdByUserId(userId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // 2. cursor ScrollPosition 변환
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 3. 충전 내역 조회
        Window<ChargeHistoryItem> window =
                transactionRepository.findChargeHistoriesByPartyId(
                        partyId, position, Limit.of(size));

        // 4. CursorPageResponse 변환 위임
        return paginationService.toCursorPage(window);
    }

    /** Transaction 내부 EXCHANGE 타입 내역 조회 */
    public CursorPageResponse<ExchangeHistoryItem> getExchangeHistories(
            Long userId, CursorPageRequest request, int size) {

        // 1. userId partyId 변환
        Long partyId =
                userRepository
                        .findPartyIdByUserId(userId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // 2. cursor ScrollPosition 변환
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 3. 환전 내역 조회
        Window<ExchangeHistoryItem> window =
                transactionRepository.findExchangeHistoriesByPartyId(
                        partyId, position, Limit.of(size));

        // 4. CursorPageResponse 변환 위임
        return paginationService.toCursorPage(window);
    }

    /** 결제 내역 페이징 조회 */
    public CursorPageResponse<PaymentHistoryItem> getUserPaymentHistory(
            Long partyId, CursorPageRequest request, int size) {

        log.info("결제 내역 조회 시작. partyId={}", partyId);

        /**
         * 1. 어디서 가져올지 파악한다. (책갈피 역할) request의 createdAt과 id를 기준 삼는다. Spring Data Jpa는 Map을 이해하지
         * 못한다. 따라서 ScrollPosition이 파라미터를 Map형태로 가지고, 쿼리 메소드의 파라미터로 가질 수 있도록 추상화 해준다.
         */
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        /**
         * 2. window 객체 반환 position을 기반으로 결제자(Users)의 지불 내역에 대한 Transaction(PAYMENT) 객체를 가져온다.
         *
         * <p>이때 Window타입은 기존 List와 다르게 다음 row가 존재하는지를 판단하는 hasNext()를 가진다.
         *
         * <p>결론적으로 (partyId, 최대로 가져올 사이즈, 책갈피 위치) 를 가지고 조회를 하게된다.
         */
        Window<Transaction> window =
                transactionRepository.findPaymentHistory(
                        partyId, List.of(TransactionStatus.SUCCESS), Limit.of(size), position);

        /**
         * 3. Merchant의 PartyId 추출
         *
         * <p>수취자(Merchant)의 Id를 가져오기 위해 결제자(Users)의 지불 내역을 기준으로 수취자(Merchant)의 PartyId를 리스트로 추출한다.
         */
        List<Long> payeePartyIds =
                window.getContent().stream().map(t -> t.getToParty().getId()).toList();

        /** 4. Merchant의 PartyId 기반으로 MerchantName 추출 */
        Map<Long, String> merchantNameMap =
                merchantRepository.findByParty_IdIn(payeePartyIds).stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.getParty().getId(), Merchant::getMerchantName));

        /** 5. paginationService.toCursorPage 형태에 맞게 response DTO 변환 */
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

        /** <T extends CursorItem> CursorPageResponse<T> 형태로 반환 */
        return paginationService.toCursorPage(responseWindow);
    }

    /** CHARGE 타입 상세 정보 조회 */
    public UserChargeHistoryDetail getUserChargeHistoryDetail(Long partyId, Long transactionId) {
        log.info("충전 내역 상세 조회 시작. partyId={}, transactionId={}", partyId, transactionId);
        // 1. CHARGE 타입의 transaction 조회
        Transaction transaction =
                findOwnedTransaction(partyId, transactionId, TransactionType.CHARGE);

        // 2. blockchain_ledger 조회 (BankClient 호출)
        BlockchainLedgerResponse blockchainLedger = findBlockchainLedger(transaction);

        log.info("충전 내역 상세 조회 완료. partyId={}, transactionId={}", partyId, transactionId);
        return UserChargeHistoryDetail.from(transaction, blockchainLedger);
    }

    /** EXCHANGE 타입 상세 정보 조회 */
    public UserExchangeHistoryDetail getUserExchangeHistoryDetail(
            Long partyId, Long transactionId) {
        log.info("환전 내역 상세 조회 시작. partyId={}, transactionId={}", partyId, transactionId);

        // 1. EXCHANGE 타입의 transaction 조회
        Transaction transaction =
                findOwnedTransaction(partyId, transactionId, TransactionType.EXCHANGE);

        // 2. blockchain_ledger 조회 (BankClient 호출)
        BlockchainLedgerResponse blockchainLedger = findBlockchainLedger(transaction);

        log.info("환전 내역 상세 조회 완료. partyId={}, transactionId={}", partyId, transactionId);
        return UserExchangeHistoryDetail.from(transaction, blockchainLedger);
    }

    /** 결제 내역 상세 조회 */
    public UserPaymentHistoryDetail getUserPaymentHistoryDetail(Long partyId, Long transactionId) {
        log.info("결제 내역 상세 조회 시작. partyId={}, transactionId={}", partyId, transactionId);

        // 1. Transaction 가져오기 (fromParty fetch join)
        Transaction transaction =
                transactionRepository
                        .findByIdWithFromParty(transactionId)
                        .orElseThrow(
                                () -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // 2. 소유주 검증
        verifyOwner(partyId, transaction);

        // 3. blockchain_ledger 조회 (BankClient 호출)
        BlockchainLedgerResponse blockchainLedger = findBlockchainLedger(transaction);

        log.info("결제 내역 상세 조회 완료. partyId={}, transactionId={}", partyId, transactionId);
        return UserPaymentHistoryDetail.from(transaction, blockchainLedger);
    }

    /** transactionId, TransactionType 에 해당하는 Transaction 조회 */
    private Transaction findOwnedTransaction(
            Long partyId, Long transactionId, TransactionType expectedType) {

        Transaction transaction =
                transactionRepository
                        .findByIdWithFromPartyAccountWallet(transactionId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.HISTORY_NOT_FOUND));

        // 올바른 타입인지 확인
        if (transaction.getTransactionType() != expectedType) {
            log.warn(
                    "거래 유형 불일치. expected={}, actual={}, transactionId={}",
                    expectedType,
                    transaction.getTransactionType(),
                    transactionId);
            throw new BusinessException(UserErrorCode.HISTORY_NOT_FOUND);
        }

        // 소유자가 올바른지 확인
        if (!transaction.getFromParty().getId().equals(partyId)) {
            log.warn(
                    "거래 내역 소유자 불일치. partyId={}, transactionId={}, ownerPartyId={}",
                    partyId,
                    transactionId,
                    transaction.getFromParty().getId());
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }

        return transaction;
    }

    /** 조회자랑 결제자가 같은지 검증 */
    private void verifyOwner(Long partyId, Transaction transaction) {
        if (!transaction.getFromParty().getId().equals(partyId)) {
            log.warn(
                    "결제 내역 소유자 불일치. partyId={}, transactionId={}, payerPartyId={}",
                    partyId,
                    transaction.getId(),
                    transaction.getFromParty().getId());

            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
    }

    /** Transaction의 txHash로 blockchain_ledger 조회 */
    private BlockchainLedgerResponse findBlockchainLedger(Transaction transaction) {
        // 1. 거래가 아직 블록체인에 기록되지 않은 상태(PENDING 등)면 null 반환
        if (transaction.getTxHash() == null) {
            return null;
        }
        try {
            // 2. bank에 blockchain_ledger 조회
            return bankClient.getBlockchainLedgerByTxHash(transaction.getTxHash());
        } catch (Exception e) {
            // 3. 조회 실패 시 null 반환 (응답에는 txHash만 포함, blockchainStatus는 null)
            log.warn("blockchain_ledger 조회 실패. txHash={}", transaction.getTxHash(), e);
            return null;
        }
    }

    /** 충전 한도 조회 메서드 */
    public ChargeLimitResponse getChargeLimit(Long partyId) {
        // 이번 달 시작일과 다음 달 시작일 계산
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        // 이번 달 누적 충전 금액 조회
        BigDecimal usedAmount =
                transactionRepository.sumMonthlyAmount(
                        partyId,
                        TransactionType.CHARGE,
                        TransactionStatus.SUCCESS,
                        startOfMonth,
                        startOfNextMonth);

        // 잔여 한도 계산, 음수 방지
        BigDecimal remainLimit = MONTHLY_LIMIT.subtract(usedAmount).max(BigDecimal.ZERO);
        log.info(
                "충전 한도 조회: partyId={}, usedAmount={}, remainLimit={}",
                partyId,
                usedAmount,
                remainLimit);

        return ChargeLimitResponse.of(
                MONTHLY_LIMIT, usedAmount, remainLimit, startOfNextMonth.toLocalDate().toString());
    }

    /** 충전 금액 및 할인 계산 메서드 */
    public ChargeCalculateResponse calculateCharge(Long partyId, BigDecimal chargeAmount) {
        // 만원 단위 검증
        if (chargeAmount.remainder(new BigDecimal("10000")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ChargeErrorCode.INVALID_UNIT);
        }

        // 이번 달 사용액 조회 후 한도 초과 여부 확인
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

        // 할인 계산, 할인율 10% 고정
        BigDecimal discountRate = new BigDecimal("0.10");
        BigDecimal discountAmount = chargeAmount.multiply(discountRate);
        BigDecimal actualPayAmount = chargeAmount.subtract(discountAmount);
        log.info(
                "충전 금액 계산: partyId={}, chargeAmount={}, actualPayAmount={}",
                partyId,
                chargeAmount,
                actualPayAmount);

        return ChargeCalculateResponse.builder()
                .chargeAmount(chargeAmount)
                .discountRate(discountRate)
                .discountAmount(discountAmount)
                .actualPayAmount(actualPayAmount)
                .build();
    }
}
