package family.fisa.hangangpay.global.exception.handler;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Business Exception 처리 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException ex) {
        // 1. 에러 로그 찍기
        log.warn("[ BusinessException ]: {}", ex.getCode().getMessage());

        // 2. 예외로 부터 코드 가져오기
        BaseErrorCode errorCode = ex.getCode();

        // 3. CustomResponse 생성
        ApiResponse<?> errorResponse = ApiResponse.onFailure(errorCode);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
}
