package family.fisa.hangangpay.domain.transaction.internal.exchange;

import org.springframework.stereotype.Component;

@Component
public class MockExchangeRequestHashGenerator implements ExchangeRequestHashGenerator {
    @Override
    public String generate(Long partyId, String uuid) {
        return null;
    }
}
