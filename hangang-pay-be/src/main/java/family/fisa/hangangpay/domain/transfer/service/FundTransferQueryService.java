package family.fisa.hangangpay.domain.transfer.service;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import family.fisa.hangangpay.domain.blockchain.repository.BlockchainTxRepository;
import family.fisa.hangangpay.domain.transfer.code.error.ChargeErrorCode;
import family.fisa.hangangpay.domain.transfer.dto.ChargeCalculateResponse;
import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.ChargeLimitResponse;
import family.fisa.hangangpay.domain.transfer.dto.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.domain.transfer.dto.response.UserExchangeHistoryDetail;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import family.fisa.hangangpay.domain.transfer.repository.FundTransferRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class FundTransferQueryService {

    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("1000000");

    private final UserRepository userRepository;
    private final FundTransferRepository fundTransferRepository;
    private final BlockchainTxRepository blockchainTxRepository;

    private final PaginationService paginationService;

    /** FundTransfer 내부 CHARGE 타입 내역 조회 */
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
                fundTransferRepository.findChargeHistoriesByPartyId(
                        partyId, position, Limit.of(size));

        // 4. CursorPageResponse 변환 위임
        return paginationService.toCursorPage(window);
    }

    /** FundTransfer 내부 EXCHANGE 타입 내역 조회 */
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
                fundTransferRepository.findExchangeHistoriesByPartyId(
                        partyId, position, Limit.of(size));

        // 4. CursorPageResponse 변환 위임
        return paginationService.toCursorPage(window);
    }

    /** CHANGE 타입 상세 정보 조회 */
    public UserChargeHistoryDetail getUserChargeHistoryDetail(Long partyId, Long fundTransferId) {
        log.info("충전 내역 상세 조회 시작. partyId={}, fundTransferId={}", partyId, fundTransferId);
        // 1. CHARGE 타입의 fundTransfer 조회
        FundTransfer fundTransfer =
                findOwnedFundTransfer(partyId, fundTransferId, TransferType.CHARGE);

        // 2. BlockchainTx 조회
        BlockchainTx blockchainTx = findBlockchainTx(fundTransferId);
        log.info("충전 내역 상세 조회 완료. partyId={}, fundTransferId={}", partyId, fundTransferId);

        return UserChargeHistoryDetail.from(fundTransfer, blockchainTx);
    }

    /** EXCHANGE 타입 상세 정보 조회 */
    public UserExchangeHistoryDetail getUserExchangeHistoryDetail(
            Long partyId, Long fundTransferId) {
        log.info("환전 내역 상세 조회 시작. partyId={}, fundTransferId={}", partyId, fundTransferId);

        FundTransfer fundTransfer =
                findOwnedFundTransfer(partyId, fundTransferId, TransferType.EXCHANGE);
        BlockchainTx blockchainTx = findBlockchainTx(fundTransferId);

        log.info("환전 내역 상세 조회 완료. partyId={}, fundTransferId={}", partyId, fundTransferId);
        return UserExchangeHistoryDetail.from(fundTransfer, blockchainTx);
    }

    /** FundTransferId, TransferType 에 해당하는 FundTransfer 조회 */
    private FundTransfer findOwnedFundTransfer(
            Long partyId, Long fundTransferId, TransferType expectedType) {

        FundTransfer fundTransfer =
                fundTransferRepository
                        .findByIdWithAccountAndWallet(fundTransferId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.HISTORY_NOT_FOUND));

        // 올바른 타입인지 확인
        if (fundTransfer.getTransferType() != expectedType) {
            log.warn(
                    "이체 유형 불일치. expected={}, actual={}, fundTransferId={}",
                    expectedType,
                    fundTransfer.getTransferType(),
                    fundTransferId);
            throw new BusinessException(UserErrorCode.HISTORY_NOT_FOUND);
        }

        // 소유자가 올바른지 확인
        if (!fundTransfer.getParty().getId().equals(partyId)) {
            log.warn(
                    "이체 내역 소유자 불일치. partyId={}, fundTransferId={}, ownerPartyId={}",
                    partyId,
                    fundTransferId,
                    fundTransfer.getParty().getId());
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }

        return fundTransfer;
    }

    /** FUND_TRANSFER 타입의 BlockchainTx 조회 */
    private BlockchainTx findBlockchainTx(Long fundTransferId) {
        return blockchainTxRepository
                .findByReferenceTypeAndReferenceId(ReferenceType.FUND_TRANSFER, fundTransferId)
                .orElse(null);
    }

    /** 충전 한도 조회 메서드 */
    public ChargeLimitResponse getChargeLimit(Long partyId) {
        // 이번 달 시작일과 다음 달 시작일 계산
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        // 이번 달 누적 충전 금액 조회
        BigDecimal usedAmount =
                fundTransferRepository.sumMonthlyAmount(
                        partyId,
                        TransferType.CHARGE,
                        TransferStatus.SUCCESS,
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
                fundTransferRepository.sumMonthlyAmount(
                        partyId,
                        TransferType.CHARGE,
                        TransferStatus.SUCCESS,
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
