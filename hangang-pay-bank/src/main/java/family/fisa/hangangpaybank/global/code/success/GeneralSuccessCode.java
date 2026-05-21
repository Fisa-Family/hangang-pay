package family.fisa.hangangpaybank.global.code.success;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum GeneralSuccessCode implements BaseSuccessCode {
    COMMON_OK(HttpStatus.OK, "COMMON_OK", "성공적으로 처리했습니다."),
    COMMON_CREATED(HttpStatus.CREATED, "COMMON_CREATED", "성공적으로 생성했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
