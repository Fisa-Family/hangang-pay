package family.fisa.hangangpay.domain.transaction.code;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum TransactionSuccessCode implements BaseSuccessCode {
    CHARGE_LIMIT_RETRIEVED(HttpStatus.OK, "CHARGE_LIMIT_RETRIEVED", "충전 한도를 성공적으로 조회했습니다."),
    CHARGE_CALCULATED(HttpStatus.OK, "CHARGE_CALCULATED", "충전 금액을 성공적으로 계산했습니다."),
    CHARGE_EXECUTED(HttpStatus.OK, "CHARGE_EXECUTED", "충전을 성공적으로 실행했습니다."),
    EXCHANGE_EXECUTED(HttpStatus.OK, "EXCHANGE_EXECUTED", "환전을 성공적으로 실행했습니다."),
    PAYMENT_EXECUTED(HttpStatus.OK, "PAYMENT_EXECUTED", "결제를 성공적으로 실행했습니다."),
    PAYMENT_CANCELLED(HttpStatus.OK, "PAYMENT_CANCELLED", "결제를 성공적으로 취소했습니다."),
    PAYMENT_HISTORY_RETRIEVED(HttpStatus.OK, "PAYMENT_HISTORY_RETRIEVED", "결제 내역을 조회했습니다."),
    PAYMENT_DETAIL_RETRIEVED(HttpStatus.OK, "PAYMENT_DETAIL_RETRIEVED", "결제 상세 내역을 조회했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
