package family.fisa.hangangpay.domain.merchant.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum MerchantErrorCode implements BaseErrorCode {
    BUSINESS_INFO_NOT_FOUND(HttpStatus.NOT_FOUND, "BUSINESS_INFO_NOT_FOUND", "사업자 정보를 찾을 수 없습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
