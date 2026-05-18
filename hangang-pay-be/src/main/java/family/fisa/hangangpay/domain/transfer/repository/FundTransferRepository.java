package family.fisa.hangangpay.domain.transfer.repository;

import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

public interface FundTransferRepository {

    Window<ChargeHistoryItem> findChargeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit of);

    Window<ExchangeHistoryItem> findExchangeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit of);

    /** 파티 식별자 기준 특정 월의 이체 유형별 누적 금액 조회 */
    BigDecimal sumMonthlyAmount(
            Long partyId,
            TransferType transferType,
            TransferStatus status,
            LocalDateTime startOfMonth,
            LocalDateTime startOfNextMonth);
}
