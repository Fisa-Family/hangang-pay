package family.fisa.hangangpay.domain.transaction.internal.cancel;

import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelRequestHashGenerator {
    private final RequestHashDigest requestHashDigest;

    /**
     * 취소 실행 멱등 해시. 멱등키가 원본 결제 uuid라 uuid는 키에 고정된다. 같은 원본 결제에서 달라질 수 있는 값은 취소 요청
     * 가맹점(merchantPartyId)뿐이므로, 그 둘로 해시해 다른 가맹점의 취소 시도를 충돌로 감지한다.
     */
    public String generate(String originalTransactionUuid, Long merchantPartyId) {
        // merchantPartyId가 핵심 식별 필드. originalTransactionUuid는 키와 중복이지만 가독성을 위해 함께 넣는다.
        return requestHashDigest.digest(originalTransactionUuid, merchantPartyId);
    }
}
