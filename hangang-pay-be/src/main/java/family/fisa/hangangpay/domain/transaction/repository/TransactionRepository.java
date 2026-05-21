package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeHistoryItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(Long id);

    /** 비즈니스 식별자(transaction_uuid)로 단건 조회 - CANCEL 시 원본 PAYMENT 조회용 */
    Optional<Transaction> findByTransactionUuid(String transactionUuid);

    /** 충전 이력 페이징 (TransactionType=CHARGE) */
    Window<ChargeHistoryItem> findChargeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit limit);

    /** 환전 이력 페이징 (TransactionType=EXCHANGE) */
    Window<ExchangeHistoryItem> findExchangeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit limit);

    /** 결제 이력 페이징 (TransactionType=PAYMENT + status IN) */
    Window<Transaction> findPaymentHistory(
            Long partyId, List<TransactionStatus> statuses, Limit limit, ScrollPosition position);

    /** CHARGE/EXCHANGE 상세 - fromParty/fromAccount/toAccount/fromWallet/toWallet fetch join */
    Optional<Transaction> findByIdWithFromPartyAccountWallet(Long id);

    /** PAYMENT 상세 - fromParty fetch join */
    Optional<Transaction> findByIdWithFromParty(Long id);

    /** 파티 식별자 기준 특정 월의 거래 유형별 누적 금액 조회 */
    BigDecimal sumMonthlyAmount(
            Long partyId,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime startOfMonth,
            LocalDateTime startOfNextMonth);
}
