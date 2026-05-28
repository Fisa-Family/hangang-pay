package family.fisa.hangangpaybank.domain.institution.dto.request;

public record CreateBankWalletRequest(Long institutionId, Long partyId, boolean merchant) {}
