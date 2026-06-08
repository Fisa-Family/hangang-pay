package family.fisa.hangangpay.domain.transaction.service.exchange;

import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeInitResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeQueryService {

    private static final BigDecimal USAGE_THRESHOLD_RATE = new BigDecimal("0.60");

    private final TransactionRepository transactionRepository;

    public ExchangeInitResponse getExchangeInit(Long partyId) {
        BigDecimal walletBalance = calculateWalletBalance(partyId);
        boolean eligible = checkEligibility(partyId);

        log.info(
                "환전 정보 조회. partyId={}, eligible={}, walletBalance={}",
                partyId,
                eligible,
                walletBalance);

        return new ExchangeInitResponse(eligible, walletBalance);
    }

    public boolean checkEligibility(Long partyId) {
        Transaction latestCharge =
                transactionRepository.findLatestSuccessCharge(partyId).orElse(null);
        if (latestCharge == null) {
            return false;
        }

        LocalDateTime chargeAt = latestCharge.getCreatedAt();

        BigDecimal chargedBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.CHARGE, chargeAt);
        BigDecimal paidBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.PAYMENT, chargeAt);
        BigDecimal exchangedBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.EXCHANGE, chargeAt);

        BigDecimal balanceBefore = chargedBefore.subtract(paidBefore).subtract(exchangedBefore);
        BigDecimal balanceAfter = balanceBefore.add(latestCharge.getAmount());
        BigDecimal threshold =
                balanceAfter.multiply(USAGE_THRESHOLD_RATE).setScale(0, RoundingMode.UP);

        BigDecimal usedSinceCharge =
                transactionRepository.sumSuccessByTypeSince(
                        partyId, TransactionType.PAYMENT, chargeAt);

        return usedSinceCharge.compareTo(threshold) >= 0;
    }

    private BigDecimal calculateWalletBalance(Long partyId) {
        BigDecimal charged =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.CHARGE);
        BigDecimal paid =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.PAYMENT);
        BigDecimal exchanged =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.EXCHANGE);
        return charged.subtract(paid).subtract(exchanged);
    }
}
