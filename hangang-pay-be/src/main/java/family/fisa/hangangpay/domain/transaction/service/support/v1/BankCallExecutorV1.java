package family.fisa.hangangpay.domain.transaction.service.support.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankErrorBody;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class BankCallExecutorV1 implements BankCallExecutor {

    private static final long BANK_RETRY_DELAY_MILLIS = 200L;
    private static final Set<String> RETRYABLE_BANK_CODES =
            Set.of("TRANSACTION_DUPLICATE_PROCESSING");

    private static final ObjectMapper BANK_ERROR_MAPPER = new ObjectMapper();

    /** Bank 쓰기 호출 + 일시적 오류 1회 재시도를 한다. bank가 transactionUuid로 멱등 처리를 하므로 재호출은 안전하다. */
    @Override
    public <T> BankOutcome<T> callBankWithRetry(
            Supplier<T> bankCall,
            Map<String, BaseErrorCode> failCodeMap,
            BaseErrorCode fallbackCode) {
        // 1. 1차 시도
        try {
            return BankOutcome.success(bankCall.get()); // Supplier 로 제네릭하게 호출
        } catch (RuntimeException first) {
            if (!isRetryable(first)) {
                return BankOutcome.failed(
                        resolveErrorCode(
                                first,
                                failCodeMap,
                                fallbackCode)); // 비지니스 로직상 불가능한 것들은 FAILED 처리 (ex: 잔액부족)
            }
            log.warn("Bank 호출 일시적 오류, 1회 재시도. reason={}", first.getMessage());

            // 2. 백오프 대기 중 인터럽트되면 재시도 포기하고 UNKNOWN (스케줄러가 정산)
            if (!sleepBeforeRetry()) {
                log.warn("재시도 대기 중 인터럽트 발생 - UNKNOWN으로 변경");
                return BankOutcome.unknown();
            }
        }

        // 3. 재시도 (같은 client 쓰기 호출)
        try {
            return BankOutcome.success(bankCall.get());
        } catch (RuntimeException retry) {
            if (isRetryable(retry)) {
                return BankOutcome.unknown(); // 여전히 불확실한 것들은 UNKNOWN 처리 후 스케줄러에게 위임
            }
            return BankOutcome.failed(
                    resolveErrorCode(
                            retry, failCodeMap, fallbackCode)); // 재시도 중 종단 실패로 확정 (보상의 보상을 하지않기 위함)
        }
    }

    /**
     * Bank에서 재시도 할만한 비지니스 예외는 TRANSACTION_DUPLICATE_PROCESSING 뿐이다. 이외에는 모두 FAILED로 보면 된다.
     *
     * <p>TRANSACTION_DUPLICATE_PROCESSING 409 일시적 → 재시도 TRANSACTION_ALREADY_FAILED 422 종단
     * TRANSACTION_INSUFFICIENT_BALANCE 400 종단 TRANSACTION_NOT_FOUND 404 종단 EXCHANGE_CONTRACT_FAILED
     * 502 종단인데 5xx ️ BLOCKCHAIN_LEDGER_NOT_FOUND 500 종단인데 5xx ️
     */
    private boolean isRetryable(RuntimeException exception) {
        /** 네트워크 I/O를 하는 도중 생기는 예외 */
        if (exception instanceof ResourceAccessException) {
            return true;
        }

        /** bankClient가 호출하는 예외 */
        if (exception instanceof RestClientResponseException rcre) {
            // 1. bankClient 응답 코드 확인 (5xx같은 코드 거르기)
            Optional<String> code = parseBankErrorCode(rcre);

            // 2. 재시도 할만한 비지니스 예외 확인
            if (code.isPresent()) {
                return RETRYABLE_BANK_CODES.contains(code.get());
            }
            // 3. code를 못읽으면 순수 5xx, 409만 재시도한다.
            return rcre.getStatusCode().is5xxServerError()
                    || rcre.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT);
        }

        return false;
    }

    /** 종단 실패의 정규화 코드 결정. */
    private BaseErrorCode resolveErrorCode(
            RuntimeException ex,
            Map<String, BaseErrorCode> failCodeMap,
            BaseErrorCode fallbackCode) {
        if (ex instanceof BusinessException be) {
            return be.getCode();
        }
        if (ex instanceof RestClientResponseException rcre) {
            String bankCode = parseBankErrorCode(rcre).orElse(null);
            return failCodeMap.getOrDefault(bankCode, fallbackCode);
        }
        return fallbackCode;
    }

    private Optional<String> parseBankErrorCode(RestClientResponseException rcre) {
        try {
            // ObjectMapper를 통해 JSON를 객체로 역직렬화하여, BankErrorBody(code, message)만 추출함.
            BankErrorBody body =
                    BANK_ERROR_MAPPER.readValue(
                            rcre.getResponseBodyAsString(), BankErrorBody.class); //
            // code 추출
            return Optional.ofNullable(body).map(BankErrorBody::code);
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    /** 백오프 대기 - 인터럽트 되면 flag 복원 후 false -> 호출부가 UNKNOWN 처리 */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(BANK_RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 플래그 복원 (셧다운 로직이 인지)
            return false;
        }
    }
}
