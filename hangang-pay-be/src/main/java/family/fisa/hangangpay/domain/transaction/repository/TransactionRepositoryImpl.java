package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.jpa.TransactionJpaRepository;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeHistoryItem;
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
    public Window<ChargeHistoryItem> findChargeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit limit) {
        // 1. CHARGE 타입으로 필터링한 Transaction 페이지 조회
        Window<Transaction> window =
                jpaRepository.findByFromParty_IdAndTransactionTypeOrderByCreatedAtDescIdDesc(
                        partyId, TransactionType.CHARGE, position, limit);

        // 2. DTO 변환
        return window.map(ChargeHistoryItem::from);
    }

    @Override
    public Window<ExchangeHistoryItem> findExchangeHistoriesByPartyId(
            Long partyId, ScrollPosition position, Limit limit) {
        // 1. EXCHANGE 타입으로 필터링한 Transaction 페이지 조회
        Window<Transaction> window =
                jpaRepository.findByFromParty_IdAndTransactionTypeOrderByCreatedAtDescIdDesc(
                        partyId, TransactionType.EXCHANGE, position, limit);

        // 2. DTO 변환
        return window.map(ExchangeHistoryItem::from);
    }

    @Override
    public Window<Transaction> findPaymentHistory(
            Long partyId, List<TransactionStatus> statuses, Limit limit, ScrollPosition position) {
        // 1. PAYMENT 타입 + status IN 으로 필터링 (서비스에서 merchantName join 후 DTO 변환)
        return jpaRepository
                .findByFromParty_IdAndTransactionTypeAndStatusInOrderByCreatedAtDescIdDesc(
                        partyId, TransactionType.PAYMENT, statuses, limit, position);
    }

    @Override
    public Optional<Transaction> findByIdWithFromPartyAccountWallet(Long id) {
        return jpaRepository.findByIdWithFromPartyAccountWallet(id);
    }

    @Override
    public Optional<Transaction> findByIdWithFromParty(Long id) {
        return jpaRepository.findByIdWithFromParty(id);
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
