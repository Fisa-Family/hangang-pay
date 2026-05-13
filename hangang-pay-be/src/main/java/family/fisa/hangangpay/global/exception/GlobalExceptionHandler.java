package family.fisa.hangangpay.global.exception;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Business Exception 처리 */
    @ExceptionHandler(BusinessException.class)
    private ResponseEntity<ApiResponse<?>> handleCustomException(BusinessException ex) {
        // 1. 에러 로그 찍기
        log.warn("[ CustomException ]: {}", ex.getCode().getMessage());

        // 2. errorCode 추출
        BaseErrorCode errorCode = ex.getCode();

        // 3. Response 생성
        ApiResponse<?> errorResponse = ApiResponse.onFailure(errorCode);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    /**
     * @Valid 유효성 검사 실패시 발생하는 예외
     */
    @ExceptionHandler(MethodValidationException.class)
    private ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        // 1. 에러 메시지를 담을 Map객체
        Map<String, String> errors = new HashMap<>();

        // 2. 각 에러 담기
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        BaseErrorCode validationErrorCode = GeneralErrorCode.VALIDATION_FAILED;

        // 3. Response 생성
        ApiResponse<Map<String, String>> errorResponse =
                ApiResponse.onFailure(
                        validationErrorCode.getStatus(),
                        validationErrorCode.getCode(),
                        validationErrorCode.getMessage(),
                        false,
                        errors);

        return ResponseEntity.status(validationErrorCode.getStatus()).body(errorResponse);
    }

    /** 예상치 못한 예외 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
        log.error("Internal Server Error", ex);

        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR_500;

        ApiResponse<String> errorResponse =
                ApiResponse.onFailure(
                        errorCode.getStatus(),
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        false,
                        null);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
}
