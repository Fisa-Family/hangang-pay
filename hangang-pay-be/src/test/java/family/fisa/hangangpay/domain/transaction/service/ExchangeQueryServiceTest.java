package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeInitResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExchangeQueryServiceTest {

    @Mock TransactionRepository transactionRepository;

    @InjectMocks ExchangeQueryService exchangeQueryService;

    private static final Long PARTY_ID = 10L;
    private static final LocalDateTime CHARGE_AT = LocalDateTime.of(2026, 5, 1, 0, 0);

    private Transaction chargeOf(String amount) {
        Transaction tx =
                Transaction.builder()
                        .transactionType(TransactionType.CHARGE)
                        .status(TransactionStatus.SUCCESS)
                        .amount(new BigDecimal(amount))
                        .build();
        ReflectionTestUtils.setField(tx, "createdAt", CHARGE_AT);
        return tx;
    }

    private void stubWalletBalance(String charged, String paid, String exchanged) {
        when(transactionRepository.sumAllSuccessByType(PARTY_ID, TransactionType.CHARGE))
                .thenReturn(new BigDecimal(charged));
        when(transactionRepository.sumAllSuccessByType(PARTY_ID, TransactionType.PAYMENT))
                .thenReturn(new BigDecimal(paid));
        when(transactionRepository.sumAllSuccessByType(PARTY_ID, TransactionType.EXCHANGE))
                .thenReturn(new BigDecimal(exchanged));
    }

    private void stubEligibilityCalculation(
            String chargedBefore,
            String paidBefore,
            String exchangedBefore,
            String chargeAmount,
            String usedSince) {
        when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                .thenReturn(Optional.of(chargeOf(chargeAmount)));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.CHARGE, CHARGE_AT))
                .thenReturn(new BigDecimal(chargedBefore));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.PAYMENT, CHARGE_AT))
                .thenReturn(new BigDecimal(paidBefore));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.EXCHANGE, CHARGE_AT))
                .thenReturn(new BigDecimal(exchangedBefore));
        when(transactionRepository.sumSuccessByTypeSince(
                        PARTY_ID, TransactionType.PAYMENT, CHARGE_AT))
                .thenReturn(new BigDecimal(usedSince));
    }

    @Nested
    @DisplayName("checkEligibility")
    class CheckEligibility {

        @Test
        @DisplayName("충전 이력 없음 -> false")
        void 충전이력_없음() {
            when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                    .thenReturn(Optional.empty());

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isFalse();
        }

        @Test
        @DisplayName("사용액이 임계값 1원 미달 -> false")
        void 임계값_미달() {
            // balanceBefore=10K, charge=60K -> balanceAfter=70K, threshold=42K, used=41999
            stubEligibilityCalculation("10000", "0", "0", "60000", "41999");

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isFalse();
        }

        @Test
        @DisplayName("사용액이 정확히 60% (threshold 도달) -> true")
        void 정확히_임계값_통과() {
            // balanceBefore=10K, charge=60K -> balanceAfter=70K, threshold=42K, used=42000
            stubEligibilityCalculation("10000", "0", "0", "60000", "42000");

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isTrue();
        }

        @Test
        @DisplayName("첫 충전 (잔액 0) + 충전 50K, 사용 30K -> true")
        void 첫_충전_통과() {
            // balanceBefore=0, charge=50K -> balanceAfter=50K, threshold=30K, used=30000
            stubEligibilityCalculation("0", "0", "0", "50000", "30000");

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isTrue();
        }
    }

    @Nested
    @DisplayName("getExchangeInit")
    class GetExchangeInit {

        @Test
        @DisplayName("지갑 잔액 = 충전 - 결제 - 환전")
        void 잔액_계산() {
            stubWalletBalance("100000", "30000", "20000");
            when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                    .thenReturn(Optional.empty());

            ExchangeInitResponse result = exchangeQueryService.getExchangeInit(PARTY_ID);

            assertThat(result.walletBalance()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("충전 이력 없으면 eligible=false")
        void 충전이력_없으면_eligible_false() {
            stubWalletBalance("0", "0", "0");
            when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                    .thenReturn(Optional.empty());

            ExchangeInitResponse result = exchangeQueryService.getExchangeInit(PARTY_ID);

            assertThat(result.eligible()).isFalse();
        }

        @Test
        @DisplayName("자격 충족하면 eligible=true, 잔액도 함께 반환")
        void 자격충족_eligible_true() {
            stubWalletBalance("130000", "42000", "0");
            stubEligibilityCalculation("10000", "0", "0", "60000", "42000");

            ExchangeInitResponse result = exchangeQueryService.getExchangeInit(PARTY_ID);

            assertThat(result.eligible()).isTrue();
            assertThat(result.walletBalance()).isEqualByComparingTo("88000");
        }
    }
}
