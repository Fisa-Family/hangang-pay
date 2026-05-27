package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeInitResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChargeQueryService {

    private final ChargeTransactionWriter chargeTransactionWriter;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("700000");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.1");

    /** 잔액, 월 한도, 계좌 목록, 대기 거래 UUID를 조합해 충전 초기화 응답 반환 */
    public ChargeInitResponse getChargeInit(Long partyId) {
        // 대기 거래 생성 or 재사용 (락·원자성 보장)
        String transactionUuid = chargeTransactionWriter.getOrCreatePending(partyId);

        List<Account> accounts = accountRepository.findAllByParty_Id(partyId);

        // 잔액 계산 (로컬 거래 내역 기준)
        BigDecimal charged =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.CHARGE);
        BigDecimal paid =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.PAYMENT);
        BigDecimal exchanged =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.EXCHANGE);
        BigDecimal balance = charged.subtract(paid).subtract(exchanged);

        // 충전 가능 금액 계산
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = YearMonth.now().plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal chargedThisMonth =
                transactionRepository.sumMonthlyAmount(
                        partyId,
                        TransactionType.CHARGE,
                        TransactionStatus.SUCCESS,
                        startOfMonth,
                        startOfNextMonth);
        // 월 한도에서 이번 달 충전액을 뺀 나머지가 이번 달 충전 가능 금액이 됨
        BigDecimal remainingLimit = MONTHLY_LIMIT.subtract(chargedThisMonth).max(BigDecimal.ZERO);

        // 응답 조합 후 반환
        return ChargeInitResponse.of(
                partyId,
                transactionUuid,
                balance,
                MONTHLY_LIMIT,
                remainingLimit,
                DISCOUNT_RATE,
                accounts);
    }
}
