package family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload;

import java.math.BigDecimal;

public record PaymentBlockchainPayload(
        String fromWalletAddress, String toWalletAddress, BigDecimal amount) {}
