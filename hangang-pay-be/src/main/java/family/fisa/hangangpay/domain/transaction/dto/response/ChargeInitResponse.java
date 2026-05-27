package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.account.entity.Account;
import java.math.BigDecimal;
import java.util.List;

public record ChargeInitResponse(
        Long partyId,
        String transactionUuid,
        BigDecimal balance,
        BigDecimal monthlyLimit,
        BigDecimal remainingLimit,
        BigDecimal discountRate,
        List<ChargeAccountResponse> accounts) {

    /** Account 목록을 포함한 충전 초기화 응답 객체 생성 */
    public static ChargeInitResponse of(
            Long partyId,
            String transactionUuid,
            BigDecimal balance,
            BigDecimal monthlyLimit,
            BigDecimal remainingLimit,
            BigDecimal discountRate,
            List<Account> accounts) {
        return new ChargeInitResponse(
                partyId,
                transactionUuid,
                balance,
                monthlyLimit,
                remainingLimit,
                discountRate,
                accounts.stream().map(ChargeAccountResponse::from).toList());
    }
}
