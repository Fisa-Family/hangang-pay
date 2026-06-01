package family.fisa.hangangpay.domain.transaction.internal.exchange;

import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import org.springframework.stereotype.Component;

@Component
public class MockExchangeRequestHashGenerator implements ExchangeRequestHashGenerator {
    @Override
    public String generate(Long partyId, ExchangeExecuteRequest request) {
        return null;
    }
}
