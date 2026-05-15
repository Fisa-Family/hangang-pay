package family.fisa.hangangpay.global.code.success;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode {
    COMMON_OK(HttpStatus.OK, "COMMON_OK", "성공적으로 처리했습니다."),
    COMMON_CREATED(HttpStatus.CREATED, "COMMON_CREATED", "성공적으로 생성했습니다."),
    COMMON_NO_CONTENT(HttpStatus.NO_CONTENT, "COMMON_NO_CONTENT", "성공했지만 콘텐츠는 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
