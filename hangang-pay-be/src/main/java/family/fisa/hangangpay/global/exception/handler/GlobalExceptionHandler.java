package family.fisa.hangangpay.global.exception.handler;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
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
        Map<String, String> errors = new HashMap<>();

        // 1. 각 에러 담기
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        // 2. Response 생성
        BaseErrorCode errorCode = GeneralErrorCode.VALIDATION_FAILED;
        ApiResponse<Map<String, String>> errorResponse = ApiResponse.onFailure(errorCode, errors);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    /** 예상치 못한 예외 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
        log.error("Internal Server Error", ex);

        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR_500;

        // 1. errorResponse 생성
        ApiResponse<String> errorResponse = ApiResponse.onFailure(errorCode, null);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
}
