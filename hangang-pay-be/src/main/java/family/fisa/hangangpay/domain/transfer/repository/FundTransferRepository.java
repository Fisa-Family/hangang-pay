package family.fisa.hangangpay.domain.transfer.repository;

import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

public interface FundTransferRepository {
    Window<ChargeHistoryItem> findChargeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit of);
}
