package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;

public record PaymentRequest(
        String transactionUuid,
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {}
