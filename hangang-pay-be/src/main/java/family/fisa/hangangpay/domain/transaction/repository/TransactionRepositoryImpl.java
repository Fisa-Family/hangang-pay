package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

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
}
