package family.fisa.hangangpay.domain.transaction.dto.bank;

import family.fisa.hangangpay.domain.transaction.entity.BankOutcomeType;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;

public record BankOutcome<T>(BankOutcomeType type, T value, BaseErrorCode errorCode) {
    public static <T> BankOutcome<T> success(T value) {
        return new BankOutcome<>(BankOutcomeType.SUCCESS, value, null);
    }

    public static <T> BankOutcome<T> failed(BaseErrorCode errorCode) {
        return new BankOutcome<>(BankOutcomeType.TERMINAL_FAILED, null, errorCode);
    }

    public static <T> BankOutcome<T> unknown() {
        return new BankOutcome<>(BankOutcomeType.UNKNOWN, null, null);
    }
}
