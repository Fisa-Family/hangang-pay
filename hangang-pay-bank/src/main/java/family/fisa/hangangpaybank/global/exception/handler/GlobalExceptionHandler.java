package family.fisa.hangangpaybank.global.exception.handler;

import family.fisa.hangangpaybank.global.code.error.GeneralErrorCode;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import family.fisa.hangangpaybank.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getCode().getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode().getStatus())
                .body(ApiResponse.onFailure(e.getCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnknownException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(GeneralErrorCode.COMMON_INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.COMMON_INTERNAL_SERVER_ERROR));
    }
}
