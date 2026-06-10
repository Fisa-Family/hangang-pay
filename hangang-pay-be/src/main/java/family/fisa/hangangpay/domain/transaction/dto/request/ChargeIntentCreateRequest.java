package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 충전 intent 생성 요청 DTO */
public record ChargeIntentCreateRequest(
        @NotBlank String transactionUuid,
        @NotNull Long institutionId,
        @NotNull Long accountId,
        @NotNull @Positive BigDecimal amount) {}
