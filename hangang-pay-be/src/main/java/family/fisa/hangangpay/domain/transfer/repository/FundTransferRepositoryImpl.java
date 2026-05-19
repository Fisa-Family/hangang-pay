package family.fisa.hangangpay.domain.transfer.repository;

import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@RequiredArgsConstructor
public class FundTransferRepositoryImpl implements FundTransferRepository {

    private final FundTransferJpaRepository fundTransferJpaRepository;

    /** 자금 이체 내역 저장 위임 */
    @Override
    public FundTransfer save(FundTransfer fundTransfer) {
        return fundTransferJpaRepository.save(fundTransfer);
    }

    @Override
    public Window<ChargeHistoryItem> findChargeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit of) {
        // 1. Window 타입으로 FundTransfer Charge 값 가져오기
        Window<FundTransfer> findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc =
                fundTransferJpaRepository.findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc(
                        partyId, TransferType.CHARGE, position, of);

        // 2. ChargeHistoryItem 타입으로 반환하여 Return
        return findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc.map(ChargeHistoryItem::from);
    }

    @Override
    public Window<ExchangeHistoryItem> findExchangeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit of) {
        // 1. Window 타입으로 FundTransfer Exchange 값 가져오기
        Window<FundTransfer> findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc =
                fundTransferJpaRepository.findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc(
                        partyId, TransferType.EXCHANGE, position, of);

        // 2. ExchangeHistoryItem 타입으로 변환하여 return
        return findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc.map(
                ExchangeHistoryItem::from);
    }

    /** 계좌·지갑 연관 데이터 포함 단건 조회 위임 */
    @Override
    public Optional<FundTransfer> findByIdWithAccountAndWallet(Long id) {
        return fundTransferJpaRepository.findByIdWithAccountAndWallet(id);
    }

    @Override
    public BigDecimal sumMonthlyAmount(
            Long partyId,
            TransferType transferType,
            TransferStatus status,
            LocalDateTime startOfMonth,
            LocalDateTime startOfNextMonth) {
        return fundTransferJpaRepository.sumMonthlyAmount(
                partyId, transferType, status, startOfMonth, startOfNextMonth);
    }
}
