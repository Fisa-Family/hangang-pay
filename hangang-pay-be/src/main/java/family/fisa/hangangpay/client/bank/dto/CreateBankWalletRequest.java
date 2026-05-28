package family.fisa.hangangpay.client.bank.dto;

public record CreateBankWalletRequest(Long institutionId, Long partyId, boolean merchant) {}
