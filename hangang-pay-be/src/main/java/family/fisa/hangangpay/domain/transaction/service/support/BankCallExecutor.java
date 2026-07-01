package family.fisa.hangangpay.domain.transaction.service.support;

import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bank 쓰기 호출의 재시도/결과 분류 엔진. PAYMENT·CANCEL 실행이 공유한다.
 *
 * <p>bank가 transactionUuid로 멱등 처리를 하므로 재호출은 안전하다.
 */
public interface BankCallExecutor {

    /**
     * Bank 쓰기 호출 + 일시적 오류 1회 재시도. 결과를 SUCCESS/UNKNOWN/TERMINAL_FAILED로 분류해 {@link BankOutcome}로
     * 반환한다.
     *
     * @param bankCall 실제 bank 쓰기 호출 (멱등)
     * @param failCodeMap bank 오류 코드 → 종단 실패 {@link BaseErrorCode} 매핑(플로우별)
     * @param fallbackCode 매핑되지 않는 종단 실패의 기본 코드
     */
    <T> BankOutcome<T> callBankWithRetry(
            Supplier<T> bankCall,
            Map<String, BaseErrorCode> failCodeMap,
            BaseErrorCode fallbackCode);
}
