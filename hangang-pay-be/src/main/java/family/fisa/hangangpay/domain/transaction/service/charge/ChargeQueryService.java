package family.fisa.hangangpay.domain.transaction.service.charge;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeInitResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.wallet.service.WalletQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargeQueryService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final WalletQueryService walletQueryService;

    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("700000");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.1");

    /** 잔액, 월 한도, 계좌 목록을 조합해 충전 초기화 응답 반환 */
    public ChargeInitResponse getChargeInit(Long partyId) {
        List<Account> accounts = accountRepository.findAllByParty_Id(partyId);

        // 잔액 - 현재 지갑 잔액을 기준으로 해야함
        BigDecimal walletBalance = walletQueryService.getBalance(partyId).getBalance();

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

        return ChargeInitResponse.of(
                partyId, walletBalance, MONTHLY_LIMIT, remainingLimit, DISCOUNT_RATE, accounts);
    }
}
