package family.fisa.hangangpay.domain.transaction.service.charge;

import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;

/** CHARGE(충전) 명령 오케스트레이터. */
public interface ChargeCommandService {

    /** 충전 intent - 금액·출금 계좌 바인딩 후 PENDING 생성 */
    ChargeIntentResponse createIntent(Long partyId, ChargeIntentCreateRequest request);

    /** 충전 실행: 멱등성 판단 → 은행 충전 요청 → 상태 전환 */
    ChargeExecuteResponse execute(
            Long partyId, String transactionUuid, ChargeExecuteRequest request);
}
