package family.fisa.hangangpay.domain.transfer.service;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import family.fisa.hangangpay.domain.blockchain.repository.BlockchainTxRepository;
import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.domain.transfer.dto.response.UserExchangeHistoryDetail;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import family.fisa.hangangpay.domain.transfer.repository.FundTransferRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
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

    private final UserRepository userRepository;
    private final FundTransferRepository fundTransferRepository;
    private final BlockchainTxRepository blockchainTxRepository;

    private final PaginationService paginationService;

    private static final int PAGE_SIZE = 20;

    /** FundTransfer 내부 CHARGE 타입 내역 조회 */
    public CursorPageResponse<ChargeHistoryItem> getChargeHistories(
            Long userId, CursorPageRequest request) {

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
                        partyId, position, Limit.of(PAGE_SIZE));

        // 4. CursorPageResponse 변환 위임
        return paginationService.toCursorPage(window);
    }

    /** FundTransfer 내부 EXCHANGE 타입 내역 조회 */
    public CursorPageResponse<ExchangeHistoryItem> getExchangeHistories(
            Long userId, CursorPageRequest request) {

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
                        partyId, position, Limit.of(PAGE_SIZE));

        // 4. CursorPageResponse 변환 위임
        return paginationService.toCursorPage(window);
    }

    /** CHARGE 타입 상세 정보 조회 */
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
}
