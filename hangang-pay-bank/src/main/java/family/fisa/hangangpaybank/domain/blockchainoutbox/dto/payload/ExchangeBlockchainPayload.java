package family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload;

import java.math.BigDecimal;

public record ExchangeBlockchainPayload(String walletAddress, BigDecimal amount) {}
