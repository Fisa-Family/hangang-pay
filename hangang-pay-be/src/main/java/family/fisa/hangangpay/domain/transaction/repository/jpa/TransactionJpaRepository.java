package family.fisa.hangangpay.domain.transaction.repository.jpa;

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
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionJpaRepository extends JpaRepository<Transaction, Long> {

    /** 비즈니스 식별자(transaction_uuid)로 단건 조회 - CANCEL 시 원본 PAYMENT 조회용 */
    @EntityGraph(attributePaths = {"fromParty", "toParty", "fromWallet", "toWallet"})
    Optional<Transaction> findByTransactionUuid(String transactionUuid);

    /** CHARGE/EXCHANGE 페이징 - 어댑터에서 .map으로 DTO 변환 */
    Window<Transaction> findByFromParty_IdAndTransactionTypeOrderByCreatedAtDescIdDesc(
            Long fromPartyId,
            TransactionType transactionType,
            ScrollPosition position,
            Limit limit);

    /** PAYMENT 페이징 - 수취자(toParty) fetch join */
    @EntityGraph(attributePaths = {"toParty"})
    Window<Transaction> findByFromParty_IdAndTransactionTypeAndStatusInOrderByCreatedAtDescIdDesc(
            Long fromPartyId,
            TransactionType transactionType,
            List<TransactionStatus> statuses,
            Limit limit,
            ScrollPosition position);

    /** CHARGE/EXCHANGE 상세 - fromParty + Account + Wallet fetch join */
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    @EntityGraph(
            attributePaths = {
                "fromParty",
                "fromAccount",
                "fromAccount.institution",
                "toAccount",
                "toAccount.institution",
                "fromWallet",
                "toWallet"
            })
    Optional<Transaction> findByIdWithFromPartyAccountWallet(@Param("id") Long id);

    /** PAYMENT 상세 - fromParty fetch join */
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    @EntityGraph(attributePaths = {"fromParty"})
    Optional<Transaction> findByIdWithFromParty(@Param("id") Long id);

    /** 파티 식별자 기준 특정 월의 거래 유형별 누적 금액 조회 */
    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
                    + "WHERE t.fromParty.id = :partyId "
                    + "AND t.transactionType = :type "
                    + "AND t.status = :status "
                    + "AND t.createdAt >= :startOfMonth "
                    + "AND t.createdAt < :startOfNextMonth")
    BigDecimal sumMonthlyAmount(
            @Param("partyId") Long partyId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("startOfMonth") LocalDateTime startOfMonth,
            @Param("startOfNextMonth") LocalDateTime startOfNextMonth);
}
