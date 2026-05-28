package family.fisa.hangangpay.auth.service;

import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.BusinessInfoResponse;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BusinessInfoQueryService {

    private static final Map<String, BusinessInfoResponse> MOCK_BUSINESS_INFOS =
            Map.of(
                    "123-45-67890",
                    new BusinessInfoResponse(
                            "123-45-67890", "성수 한강카페", "김한강", "서울 성동구 왕십리로 125", "카페"),
                    "234-56-78901",
                    new BusinessInfoResponse(
                            "234-56-78901", "뚝섬 분식", "이성수", "서울 성동구 상원길 40", "음식점"),
                    "345-67-89012",
                    new BusinessInfoResponse(
                            "345-67-89012", "서울숲 서점", "박서울", "서울 성동구 서울숲2길 32", "소매업"));

    public BusinessInfoResponse getBusinessInfo(String businessNumber) {
        BusinessInfoResponse businessInfo = MOCK_BUSINESS_INFOS.get(businessNumber);

        if (businessInfo == null) {
            throw new BusinessException(MerchantErrorCode.BUSINESS_INFO_NOT_FOUND);
        }

        return businessInfo;
    }
}
