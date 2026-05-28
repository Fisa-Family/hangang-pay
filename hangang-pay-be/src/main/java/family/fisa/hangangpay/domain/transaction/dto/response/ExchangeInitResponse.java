package family.fisa.hangangpay.domain.transaction.dto.response;

import java.math.BigDecimal;

public record ExchangeInitResponse(boolean eligible, BigDecimal walletBalance) {}
