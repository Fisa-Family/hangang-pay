package family.fisa.hangangpay.domain.transaction.dto.user.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 환전 intent 생성 요청. transactionUuid는 서버가 발급하므로 받지 않는다. */
public record ExchangeIntentCreateRequest(@NotNull @Positive BigDecimal amount) {}
