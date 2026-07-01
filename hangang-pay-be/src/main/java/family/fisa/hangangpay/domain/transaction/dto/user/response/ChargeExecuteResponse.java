package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeExecuteResponse(
        Long partyId,
        Long chargeId,
        BigDecimal amount,
        BigDecimal finalAmount,
        BigDecimal walletBalance,
        LocalDateTime chargedAt) {

    /** Transaction 엔티티와 확정 시각으로 응답 DTO 생성 */
    public static ChargeExecuteResponse from(Transaction t, LocalDateTime chargedAt) {
        return from(t, chargedAt, null);
    }

    /** Transaction 엔티티, 확정 시각, 처리 후 지갑 잔액으로 응답 DTO 생성 */
    public static ChargeExecuteResponse from(
            Transaction t, LocalDateTime chargedAt, BigDecimal walletBalance) {
        BigDecimal discountAmount =
                t.getDiscountAmount() != null ? t.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalAmount =
                t.getAmount() != null ? t.getAmount().subtract(discountAmount) : BigDecimal.ZERO;
        return new ChargeExecuteResponse(
                t.getFromParty().getId(),
                t.getId(),
                t.getAmount(),
                finalAmount,
                walletBalance,
                chargedAt);
    }
}
