package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;
    private final EntityManager entityManager;

    @Override
    public Transaction save(Transaction transaction) {
        return jpaRepository.save(transaction);
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Transaction> findByTransactionUuid(String transactionUuid) {
        return jpaRepository.findByTransactionUuid(transactionUuid);
    }

    @Override
    public Window<Transaction> findTransactionByPartyId(
            Long partyId,
            TransactionStatus status,
            List<TransactionType> types,
            ScrollPosition position,
            Limit limit) {

        return jpaRepository
                .findByFromParty_IdAndStatusAndTransactionTypeInOrderByCreatedAtDescIdDesc(
                        partyId, status, types, position, limit);
    }

    @Override
    public Window<Transaction> findPaymentTransactionsByMerchantPartyId(
            Long partyId, TransactionStatus status, ScrollPosition position, Limit limit) {
        StringBuilder jpql =
                new StringBuilder(
                        """
                        SELECT t FROM Transaction t
                        WHERE t.status = :status
                          AND (
                            (t.transactionType = :paymentType AND t.toParty.id = :merchantPartyId)
                            OR
                            (t.transactionType = :cancelType AND t.fromParty.id = :merchantPartyId)
                          )
                        """);

        boolean hasCursor =
                position instanceof KeysetScrollPosition keysetPosition
                        && !keysetPosition.isInitial();
        if (hasCursor) {
            jpql.append(
                    """
                      AND (
                        t.createdAt < :cursorCreatedAt
                        OR (t.createdAt = :cursorCreatedAt AND t.id < :cursorId)
                      )
                    """);
        }

        jpql.append(" ORDER BY t.createdAt DESC, t.id DESC");

        TypedQuery<Transaction> query =
                entityManager.createQuery(jpql.toString(), Transaction.class);
        query.setParameter("status", status);
        query.setParameter("paymentType", TransactionType.PAYMENT);
        query.setParameter("cancelType", TransactionType.CANCEL);
        query.setParameter("merchantPartyId", partyId);

        if (hasCursor) {
            Map<String, Object> keys = ((KeysetScrollPosition) position).getKeys();
            query.setParameter("cursorCreatedAt", keys.get("createdAt"));
            query.setParameter("cursorId", keys.get("id"));
        }

        int pageSize = limit.isLimited() ? limit.max() : Integer.MAX_VALUE;
        query.setMaxResults(limit.isLimited() ? pageSize + 1 : pageSize);

        List<Transaction> fetched = query.getResultList();
        boolean hasNext = limit.isLimited() && fetched.size() > pageSize;
        List<Transaction> content =
                hasNext ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;

        return Window.from(
                content,
                index ->
                        ScrollPosition.of(
                                Map.of(
                                        "createdAt", content.get(index).getCreatedAt(),
                                        "id", content.get(index).getId()),
                                KeysetScrollPosition.Direction.FORWARD),
                hasNext);
    }

    @Override
    public Optional<Transaction> findDetailByIdAndTypes(Long id, List<TransactionType> types) {
        return jpaRepository.findByIdAndTransactionTypeIn(id, types);
    }

    @Override
    public List<Transaction> findAllUnknownPayments() {
        return jpaRepository.findByStatusAndTransactionType(
                TransactionStatus.UNKNOWN, TransactionType.PAYMENT);
    }

    @Override
    public BigDecimal sumMonthlyAmount(
            Long partyId,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime startOfMonth,
            LocalDateTime startOfNextMonth) {
        return jpaRepository.sumMonthlyAmount(
                partyId, type, status, startOfMonth, startOfNextMonth);
    }

    @Override
    public Optional<Transaction> findLatestSuccessCharge(Long partyId) {
        return jpaRepository
                .findFirstByFromParty_IdAndTransactionTypeAndStatusOrderByCreatedAtDescIdDesc(
                        partyId, TransactionType.CHARGE, TransactionStatus.SUCCESS);
    }

    @Override
    public BigDecimal sumSuccessByTypeBefore(
            Long partyId, TransactionType type, LocalDateTime before) {
        return jpaRepository.sumSuccessByTypeBefore(partyId, type, before);
    }

    @Override
    public BigDecimal sumSuccessByTypeSince(
            Long partyId, TransactionType type, LocalDateTime since) {
        return jpaRepository.sumSuccessByTypeSince(partyId, type, since);
    }

    @Override
    public boolean existsInflightExchange(Long partyId) {
        return jpaRepository.existsByFromParty_IdAndTransactionTypeAndStatus(
                partyId, TransactionType.EXCHANGE, TransactionStatus.PENDING);
    }

    @Override
    public List<Long> findPendingExchangeIdsForReconcile(LocalDateTime threshold, int maxAttempts) {
        return jpaRepository.findIdsForReconcile(
                TransactionStatus.PENDING, TransactionType.EXCHANGE, threshold, maxAttempts);
    }
}
