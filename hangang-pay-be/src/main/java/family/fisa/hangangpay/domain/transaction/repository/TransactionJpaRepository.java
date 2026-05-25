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
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionJpaRepository extends JpaRepository<Transaction, Long> {

    /**
     * 비즈니스 식별자(transaction_uuid)로 단건 조회
     */
    @EntityGraph(
        attributePaths = {
            "fromParty",
            "toParty",
            "fromAccount",
            "toAccount",
            "toAccount.institution",
            "fromWallet",
            "toWallet"
        })
    Optional<Transaction> findByTransactionUuid(String transactionUuid);

    /**
     * 거래 이력 페이징 - 수취자(toParty) fetch join
     */
    @EntityGraph(attributePaths = {"toParty"})
    Window<Transaction> findByFromParty_IdAndStatusAndTransactionTypeInOrderByCreatedAtDescIdDesc(
        Long fromPartyId,
        TransactionStatus status,
        List<TransactionType> transactionTypes,
        ScrollPosition position,
        Limit limit);

    /**
     * 거래 상세 - fromParty + Account + Wallet fetch join
     */
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.transactionType IN :types")
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
    Optional<Transaction> findByIdAndTransactionTypeIn(
        @Param("id") Long id, @Param("types") List<TransactionType> types);

    /**
     * PAYMENT 상세 - fromParty fetch join
     */
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    @EntityGraph(attributePaths = {"fromParty"})
    Optional<Transaction> findByIdWithFromParty(@Param("id") Long id);

    /**
     * 파티 식별자 기준 특정 월의 거래 유형별 누적 금액 조회
     */
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

    /**
     * 가장 최근 SUCCESS CHARGE 1건
     */
    Optional<Transaction>
    findFirstByFromParty_IdAndTransactionTypeAndStatusOrderByCreatedAtDescIdDesc(
        Long fromPartyId, TransactionType transactionType, TransactionStatus status);

    /**
     * 특정 시점 이전(exclusive)의 SUCCESS 거래 타입별 누적 금액
     */
    @Query(
        "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
            + "WHERE t.fromParty.id = :partyId "
            + "AND t.transactionType = :type "
            + "AND t.status = "
            + "  family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.SUCCESS "
            + "AND t.createdAt < :before")
    BigDecimal sumSuccessByTypeBefore(
        @Param("partyId") Long partyId,
        @Param("type") TransactionType type,
        @Param("before") LocalDateTime before);

    /**
     * 특정 시점 이후(inclusive)의 SUCCESS 거래 타입별 누적 금액
     */
    @Query(
        "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
            + "WHERE t.fromParty.id = :partyId "
            + "AND t.transactionType = :type "
            + "AND t.status = "
            + "  family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.SUCCESS "
            + "AND t.createdAt >= :since")
    BigDecimal sumSuccessByTypeSince(
        @Param("partyId") Long partyId,
        @Param("type") TransactionType type,
        @Param("since") LocalDateTime since);

    /**
     * 진행 중인 EXCHANGE 존재 여부
     */
    boolean existsByFromParty_IdAndTransactionTypeAndStatus(
        Long fromPartyId, TransactionType transactionType, TransactionStatus status);

    /**
     * 배치 reconcile 대상 id 조회
     */
    @Query(
        "SELECT t.id FROM Transaction t "
            + "WHERE t.status = :status "
            + "AND t.transactionType = :type "
            + "AND t.createdAt < :threshold "
            + "AND t.reconcileAttemptCount < :maxAttempts")
    List<Long> findIdsForReconcile(
        @Param("status") TransactionStatus status,
        @Param("type") TransactionType type,
        @Param("threshold") LocalDateTime threshold,
        @Param("maxAttempts") int maxAttempts);
}
