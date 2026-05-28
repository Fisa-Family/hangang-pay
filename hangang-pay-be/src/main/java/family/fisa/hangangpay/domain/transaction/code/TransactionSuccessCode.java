package family.fisa.hangangpay.domain.transaction.code;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum TransactionSuccessCode implements BaseSuccessCode {
    CHARGE_INFO_RETRIEVED(HttpStatus.OK, "CHARGE_INFO_RETRIEVED", "충전 정보를 조회했습니다."),
    CHARGE_LIMIT_RETRIEVED(HttpStatus.OK, "CHARGE_LIMIT_RETRIEVED", "충전 한도를 성공적으로 조회했습니다."),
    CHARGE_CALCULATED(HttpStatus.OK, "CHARGE_CALCULATED", "충전 금액을 성공적으로 계산했습니다."),
    CHARGE_EXECUTED(HttpStatus.CREATED, "CHARGE_EXECUTED", "충전이 완료되었습니다."),
    EXCHANGE_INFO_RETRIEVED(HttpStatus.OK, "EXCHANGE_INFO_RETRIEVED", "환전 정보를 조회했습니다."),
    EXCHANGE_EXECUTED(HttpStatus.OK, "EXCHANGE_EXECUTED", "환전을 성공적으로 실행했습니다."),
    PAYMENT_INTENT_CREATED(HttpStatus.CREATED, "PAYMENT_INTENT_CREATED", "결제 의도를 성공적으로 생성했습니다."),
    PAYMENT_EXECUTED(HttpStatus.OK, "PAYMENT_EXECUTED", "결제를 성공적으로 실행했습니다."),
    PAYMENT_RECOVERED(HttpStatus.OK, "PAYMENT_RECOVERED", "결제 상태를 성공적으로 복구했습니다."),
    PAYMENT_CANCELLED(HttpStatus.OK, "PAYMENT_CANCELLED", "결제를 성공적으로 취소했습니다."),
    CANCEL_RECOVERED(HttpStatus.OK, "CANCEL_RECOVERED", "결제 취소 상태를 복구했습니다."),
    PAYMENT_HISTORY_RETRIEVED(HttpStatus.OK, "PAYMENT_HISTORY_RETRIEVED", "결제 내역을 조회했습니다."),
    PAYMENT_DETAIL_RETRIEVED(HttpStatus.OK, "PAYMENT_DETAIL_RETRIEVED", "결제 상세 내역을 조회했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
