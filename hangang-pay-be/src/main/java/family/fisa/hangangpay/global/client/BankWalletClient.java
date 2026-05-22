package family.fisa.hangangpay.global.client;

import family.fisa.hangangpay.global.client.dto.BankApiResponse;
import family.fisa.hangangpay.global.client.dto.BankWalletResult;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** bank 서비스 지갑 HTTP 클라이언트 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankWalletClient {

    private final RestTemplate restTemplate;

    @Value("${app.bank-service.url:http://localhost:8081}")
    private String bankServiceUrl;

    /*
     * 지갑 주소 기준 예금 토큰 잔액 조회
     * GET /api/v1/bank-wallets/address/{walletAddress} 호출
     */
    public BigDecimal getBalance(String walletAddress) {
        String url = bankServiceUrl + "/api/v1/bank-wallets/address/" + walletAddress;
        try {
            // bank 서비스 REST 호출
            ResponseEntity<BankApiResponse<BankWalletResult>> responseEntity =
                    restTemplate.exchange(
                            url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

            BankApiResponse<BankWalletResult> body = responseEntity.getBody();

            // 응답 유효성 확인
            if (body == null || !body.isSuccess() || body.getResult() == null) {
                log.error("bank 서비스 잔액 조회 응답 오류: walletAddress={}", walletAddress);
                throw new BusinessException(GeneralErrorCode.BANK_SERVER_ERROR);
            }

            return body.getResult().balance();

        } catch (RestClientException e) {
            log.error("bank 서비스 호출 실패: walletAddress={}", walletAddress, e);
            throw new BusinessException(GeneralErrorCode.BANK_CALL_FAILED);
        }
    }
}
