package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;

public record CreateBankAccountRequest(
        Long institutionId, String accountNumber, String ownerName, BigDecimal initialBalance) {}
