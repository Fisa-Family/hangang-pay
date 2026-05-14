package family.fisa.hangangpay.domain.payment.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record UserPaymentHistoryListResponse(
        BigDecimal monthlyTotal, List<UserPaymentHistoryResponse> payments) {}
