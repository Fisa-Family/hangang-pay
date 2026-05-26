package family.fisa.hangangpay.domain.transaction.infra.redis;

import family.fisa.hangangpay.domain.transaction.internal.PaymentRateLimiter;
import org.springframework.stereotype.Component;

@Component
public class RedisPaymentRateLimiter implements PaymentRateLimiter {
    @Override
    public void checkIntentRateLimit(Long partyId, Long merchantPartyId) {}

    @Override
    public void checkExecutionRateLimit(
            Long partyId, Long merchantPartyId, String transactionUuid) {}

    @Override
    public void checkRecoveryRateLimit(Long partyId, String transactionUuid) {}

    @Override
    public void checkBankOutboundRateLimit() {}
}
