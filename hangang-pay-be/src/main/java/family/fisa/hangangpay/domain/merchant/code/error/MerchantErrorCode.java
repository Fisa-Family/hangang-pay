package family.fisa.hangangpay.domain.merchant.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum MerchantErrorCode implements BaseErrorCode {
    BUSINESS_INFO_NOT_FOUND(HttpStatus.NOT_FOUND, "BUSINESS_INFO_NOT_FOUND", "사업자 정보를 찾을 수 없습니다"),

    MERCHANT_NOT_FOUND(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "가맹점 정보를 찾을 수 없습니다."),

    MERCHANT_FORBIDDEN_MERCHANT(
            HttpStatus.FORBIDDEN, "MERCHANT_FORBIDDEN_MERCHANT", "가맹점 권한이 필요합니다"),

    MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND, "MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND", "정산 계좌를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
