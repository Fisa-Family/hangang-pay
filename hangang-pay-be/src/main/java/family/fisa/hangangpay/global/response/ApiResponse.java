package family.fisa.hangangpay.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.http.HttpStatus;

@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder({"isSuccess", "status", "code", "message", "result"})
@JsonInclude(Include.NON_NULL)
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @JsonProperty("status")
    private HttpStatus status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private T result;

    /** 성공 + 반환 데이터 없음 */
    public static ApiResponse<?> onSuccess(BaseSuccessCode baseSuccessCode) {
        return ApiResponse.builder()
                .isSuccess(true)
                .status(baseSuccessCode.getStatus())
                .code(baseSuccessCode.getCode())
                .message(baseSuccessCode.getMessage())
                .build();
    }

    /** 성공 + 반환 데이터 있음 */
    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode baseSuccessCode, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(true)
                .status(baseSuccessCode.getStatus())
                .code(baseSuccessCode.getCode())
                .message(baseSuccessCode.getMessage())
                .result(result)
                .build();
    }

    /** 실패 + 반환 값 없음 */
    public static ApiResponse<?> onFailure(BaseErrorCode errorCode) {
        return ApiResponse.builder()
                .isSuccess(false)
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    /** 실패 + 반환 값 있음 */
    public static <T> ApiResponse<T> onFailure(
            HttpStatus status, String code, String message, boolean isSuccess, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(isSuccess)
                .status(status)
                .code(code)
                .message(message)
                .result(result)
                .build();
    }
}
