package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
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

    /** 거래 이력 페이징 (TransactionStatus=SUCCESS, TransactionType= ?) */
    Window<Transaction> findTransactionByPartyId(
            Long partyId,
            TransactionStatus status,
            List<TransactionType> types,
            ScrollPosition position,
            Limit limit);

    /** 거래 상세 - id + type IN, fromParty/fromAccount/toAccount/fromWallet/toWallet fetch join */
    Optional<Transaction> findDetailByIdAndTypes(Long id, List<TransactionType> types);

    /** 스케줄러용 - UNKNOWN 상태 PAYMENT 트랜잭션 전체 조회 */
    List<Transaction> findAllUnknownPayments();

    /** 파티 식별자 기준 특정 월의 거래 유형별 누적 금액 조회 */
    BigDecimal sumMonthlyAmount(
            Long partyId,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime startOfMonth,
            LocalDateTime startOfNextMonth);
}
