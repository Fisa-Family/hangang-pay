package family.fisa.hangangpay.domain.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserPaymentHistoryResponse(
        String paymentId, String merchantName, BigDecimal amount, LocalDateTime paidAt) {}
