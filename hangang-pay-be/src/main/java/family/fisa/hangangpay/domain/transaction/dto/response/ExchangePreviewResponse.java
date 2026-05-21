package family.fisa.hangangpay.domain.transaction.dto.response;

import java.math.BigDecimal;

public record ExchangePreviewResponse(
        BigDecimal exchangeAmount,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        BigDecimal actualReceiveAmount) {}
