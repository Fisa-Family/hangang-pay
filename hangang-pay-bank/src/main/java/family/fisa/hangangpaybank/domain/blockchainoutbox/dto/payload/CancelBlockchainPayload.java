package family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload;

import java.math.BigDecimal;

public record CancelBlockchainPayload(
        String originalTransactionUuid,
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {}
