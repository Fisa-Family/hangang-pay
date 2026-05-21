package family.fisa.hangangpay.domain.transaction.dto.response;

import java.math.BigDecimal;

public record ExchangeEligibilityResponse(
        boolean eligible,
        String reason,
        BigDecimal availableAmount,
        BigDecimal monthlyUsed,
        BigDecimal monthlyLimit) {}
