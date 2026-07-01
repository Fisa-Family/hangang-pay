package family.fisa.hangangpay.domain.transaction.dto.user.request;

import java.math.BigDecimal;

public record PaymentIntentCreateRequest(
        Long merchantPartyId, BigDecimal amount, String itemName) {}
