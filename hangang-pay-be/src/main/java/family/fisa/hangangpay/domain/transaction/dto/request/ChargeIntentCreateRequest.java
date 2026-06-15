package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 충전 intent 생성 요청 DTO. transactionUuid는 서버가 발급하므로 받지 않는다. */
public record ChargeIntentCreateRequest(
        @NotNull Long institutionId,
        @NotNull Long accountId,
        @NotNull @Positive BigDecimal amount) {}
