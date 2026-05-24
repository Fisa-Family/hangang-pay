package family.fisa.hangangpay.domain.merchant.dto;

import java.util.List;

public record MerchantSettlementHistoryResponse(
        List<MerchantSettlementHistoryItem> items, String hasNext, String nextScrollPosition) {}
