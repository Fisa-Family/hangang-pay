package family.fisa.hangangpay.domain.user.code;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum UserSuccessCode implements BaseSuccessCode {
    CHARGE_HISTORIES_RETRIEVED(HttpStatus.OK, "CHARGE_HISTORIES_RETRIEVED", "충전내역을 성공적으로 조회했습니다."),
    REFUND_HISTORIES_RETRIEVED(HttpStatus.OK, "REFUND_HISTORIES_RETRIEVED", "환전내역을 성공적으로 조회했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
