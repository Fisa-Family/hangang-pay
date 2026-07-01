package family.fisa.hangangpay.domain.transaction.service.exchange;

import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;

/** EXCHANGE(환전) 명령 오케스트레이터. 소비자·가맹점 공통. */
public interface ExchangeCommandService {

    /** 사용자 환전 intent - PRIMARY, 자격 검증 후 PENDING 생성 */
    ExchangeIntentResponse createUserIntent(Long partyId, ExchangeIntentCreateRequest request);

    /** 가맹점 환전 intent - SETTLEMENT, 자격 검증 없음 */
    ExchangeIntentResponse createMerchantIntent(Long partyId, ExchangeIntentCreateRequest request);

    /** 사용자 환전 실행 */
    ExchangeExecuteResponse executeUserExchange(
            Long partyId, String uuid, ExchangeExecuteRequest request);

    /** 가맹점 환전 실행 */
    ExchangeExecuteResponse executeMerchantExchange(
            Long partyId, String uuid, ExchangeExecuteRequest request);
}
