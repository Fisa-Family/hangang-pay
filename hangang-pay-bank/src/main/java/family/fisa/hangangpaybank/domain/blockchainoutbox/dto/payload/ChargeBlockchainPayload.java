package family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload;

import java.math.BigDecimal;

public record ChargeBlockchainPayload(String walletAddress, BigDecimal mintAmount) {}
