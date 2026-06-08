package family.fisa.hangangpay.domain.transaction.internal.exchange;

import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeRequestHashGenerator {
    private final RequestHashDigest requestHashDigest;

    /**
     * 환전 실행 멱등 해시. 금액은 intent 단계에서 uuid에 고정되므로, 같은 uuid에서 요청마다 달라질 수 있는 값은 요청자(partyId)뿐이다. 따라서
     * partyId + uuid로 해시해 다른 사용자가 같은 uuid를 재사용하는 경우를 충돌로 감지한다.
     */
    public String generate(Long partyId, String uuid) {
        // 2. partyId가 핵심 식별 필드. uuid는 키 자체와 중복이지만 가독성을 위해 함께 넣는다.
        return requestHashDigest.digest(partyId, uuid);
    }
}
