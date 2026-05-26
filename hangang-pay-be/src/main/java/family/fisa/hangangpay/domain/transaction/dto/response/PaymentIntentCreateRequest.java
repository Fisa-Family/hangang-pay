package family.fisa.hangangpay.domain.transaction.dto.request;

import java.math.BigDecimal;

public record PaymentIntentCreateRequest(
        Long merchantPartyId, BigDecimal amount, String itemName) {}
