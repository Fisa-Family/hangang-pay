package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;

public record ChargeAccountResponse(
        Long accountId,
        Long institutionId,
        String institutionCode,
        String institutionName,
        String accountNumber,
        boolean isPrimary) {

    /** Account 엔티티를 충전 계좌 응답 DTO로 변환 */
    public static ChargeAccountResponse from(Account account) {
        return new ChargeAccountResponse(
                account.getId(),
                account.getInstitution().getId(),
                account.getInstitution().getInstitutionCode(),
                account.getInstitution().getInstitutionName(),
                account.getAccountNumber(),
                account.getAccountType() == AccountType.PRIMARY);
    }
}
