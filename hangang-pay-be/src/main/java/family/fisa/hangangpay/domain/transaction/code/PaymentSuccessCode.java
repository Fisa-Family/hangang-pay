package family.fisa.hangangpay.domain.transaction.code;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum PaymentSuccessCode implements BaseSuccessCode {
    PAYMENT_EXECUTED(HttpStatus.OK, "PAYMENT_EXECUTED", "결제를 성공적으로 실행했습니다."),
    PAYMENT_CANCELLED(HttpStatus.OK, "PAYMENT_CANCELLED", "결제를 성공적으로 취소했습니다."),
    PAYMENT_HISTORY_RETRIEVED(HttpStatus.OK, "PAYMENT_HISTORY_RETRIEVED", "결제 내역을 조회했습니다."),
    PAYMENT_DETAIL_RETRIEVED(HttpStatus.OK, "PAYMENT_DETAIL_RETRIEVED", "결제 상세 내역을 조회했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
