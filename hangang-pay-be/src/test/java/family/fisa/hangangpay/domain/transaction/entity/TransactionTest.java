package family.fisa.hangangpay.domain.transaction.entity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionTest {

    @Test
    @DisplayName("PENDING 결제는 실행 검증을 통과한다")
    void validateExecutableStatus_pendingPasses() {
        Transaction transaction = paymentWith(TransactionStatus.PENDING);

        assertThatCode(transaction::validateExecutableStatus).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("EXPIRED 결제는 PAYMENT_INTENT_EXPIRED 예외가 발생한다")
    void validateExecutableStatus_expiredThrowsIntentExpired() {
        Transaction transaction = paymentWith(TransactionStatus.EXPIRED);

        assertThatThrownBy(transaction::validateExecutableStatus)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_INTENT_EXPIRED);
    }

    @Test
    @DisplayName("PENDING·EXPIRED 외 상태는 INVALID_PAYMENT_STATUS 예외가 발생한다")
    void validateExecutableStatus_otherStatusThrowsInvalid() {
        Transaction transaction = paymentWith(TransactionStatus.SUCCESS);

        assertThatThrownBy(transaction::validateExecutableStatus)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.INVALID_PAYMENT_STATUS);
    }

    private Transaction paymentWith(TransactionStatus status) {
        return Transaction.builder()
                .transactionUuid("11111111-1111-1111-1111-111111111111")
                .transactionType(TransactionType.PAYMENT)
                .status(status)
                .amount(new BigDecimal("10000"))
                .build();
    }
}
