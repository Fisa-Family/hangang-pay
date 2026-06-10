package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;

public record ExchangeResponse(
        String transactionUuid, Long bankTransactionId, String status, BigDecimal accountBalance) {}
