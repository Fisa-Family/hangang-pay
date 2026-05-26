package family.fisa.hangangpay.client.bank.dto;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import lombok.Builder;

@Builder
public record MerchantRedeemAccountInfo(
        String institutionName, String accountNumber, AccountType accountType) {

    public static MerchantRedeemAccountInfo from(Account account) {
        return MerchantRedeemAccountInfo.builder()
                .institutionName(account.getInstitution().getInstitutionName())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .build();
    }
}
