package family.fisa.hangangpay.domain.transfer.repository;

import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.FundTransferRepository;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
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

    @Override
    public Window<ChargeHistoryItem> findChargeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit of) {
        // 1. Window 타입으로 FundTransfer 가져오기
        Window<FundTransfer> findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc =
                fundTransferJpaRepository.findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc(
                        partyId, TransferType.CHARGE, position, of);

        // 2. ChargeHistoryItem 타입으로 반환하여 Return
        return findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc.map(ChargeHistoryItem::from);
    }
}
