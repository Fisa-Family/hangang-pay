package family.fisa.hangangpay.domain.merchant.code;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MerchantErrorCode implements BaseErrorCode {
    FORBIDDEN_MERCHANT(HttpStatus.FORBIDDEN, "FORBIDDEN_MERCHANT", "가맹점 권한이 필요합니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "NOT_OWNER", "본인 가맹점의 결제 내역만 조회할 수 있습니다."),
    MERCHANT_NOT_FOUND(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "가맹점 정보를 찾을 수 없습니다."),
    QR_IMAGE_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "QR_IMAGE_GENERATION_FAILED", "QR 이미지 생성에 실패했습니다."),
    BUSINESS_INFO_NOT_FOUND(HttpStatus.NOT_FOUND, "BUSINESS_INFO_NOT_FOUND", "사업자 정보를 찾을 수 없습니다"),
    MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND, "MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND", "정산 계좌를 찾을 수 없습니다.");
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
