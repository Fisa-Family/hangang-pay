package family.fisa.hangangpay.domain.transaction.internal;

public interface PaymentRateLimiter {
    void checkIntentRateLimit(Long partyId, Long merchantPartyId);

    void checkExecutionRateLimit(Long partyId, Long merchantPartyId, String transactionUuid);

    void checkRecoveryRateLimit(Long partyId, String transactionUuid);

    void checkBankOutboundRateLimit();
}
