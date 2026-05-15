package family.fisa.hangangpay.domain.transfer.code;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum TransferSuccessCode implements BaseSuccessCode {
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
