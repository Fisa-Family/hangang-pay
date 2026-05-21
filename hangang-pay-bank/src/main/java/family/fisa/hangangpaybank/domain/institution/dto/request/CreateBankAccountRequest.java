package family.fisa.hangangpaybank.domain.institution.dto.request;

import java.math.BigDecimal;

public record CreateBankAccountRequest(
        Long institutionId, String accountNumber, String ownerName, BigDecimal initialBalance) {}
