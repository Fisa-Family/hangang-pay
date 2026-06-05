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

    /** 거래 저장 */
    @Override
    public Transaction save(Transaction transaction) {
        return jpaRepository.save(transaction);
    }

    /** 거래 저장 후 즉시 flush */
    @Override
    public Transaction saveAndFlush(Transaction transaction) {
        return jpaRepository.saveAndFlush(transaction);
    }

    /** PK로 거래 단건 조회 */
    @Override
    public Optional<Transaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    /** 비즈니스 식별자(UUID)로 거래 단건 조회 */
    @Override
    public Optional<Transaction> findByTransactionUuid(String transactionUuid) {
        return jpaRepository.findByTransactionUuid(transactionUuid);
    }

    /** 사용자 거래 이력 커서 페이징 (상태, 유형 필터) */
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

    /** 가맹점 수취 결제, 취소 이력 커서 페이징 */
    @Override
    public Window<Transaction> findPaymentTransactionsByMerchantPartyId(
            Long partyId, TransactionStatus status, ScrollPosition position, Limit limit) {
        return jpaRepository
                .findByStatusAndTransactionTypeAndToParty_IdOrStatusAndTransactionTypeAndFromParty_IdOrderByCreatedAtDescIdDesc(
                        status,
                        TransactionType.PAYMENT,
                        partyId,
                        status,
                        TransactionType.CANCEL,
                        partyId,
                        position,
                        limit);
    }

    /** id + 거래유형 목록으로 거래 상세 조회 */
    @Override
    public Optional<Transaction> findDetailByIdAndTypes(Long id, List<TransactionType> types) {
        return jpaRepository.findByIdAndTransactionTypeIn(id, types);
    }

    /** 스케줄러용 - UNKNOWN 상태 PAYMENT 전체 목록 조회 */
    @Override
    public List<Transaction> findAllUnknownByType(TransactionType type) {
        return jpaRepository.findByStatusAndTransactionType(TransactionStatus.UNKNOWN, type);
    }

    /** 특정 월 거래 유형별 누적 금액 조회 */
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

    /** 가장 최근 SUCCESS CHARGE 1건 조회 */
    @Override
    public Optional<Transaction> findLatestSuccessCharge(Long partyId) {
        return jpaRepository
                .findFirstByFromParty_IdAndTransactionTypeAndStatusOrderByCreatedAtDescIdDesc(
                        partyId, TransactionType.CHARGE, TransactionStatus.SUCCESS);
    }

    /** 특정 시점 이전(exclusive) SUCCESS 거래 유형별 누적 금액 */
    @Override
    public BigDecimal sumSuccessByTypeBefore(
            Long partyId, TransactionType type, LocalDateTime before) {
        return jpaRepository.sumSuccessByTypeBefore(partyId, type, before);
    }

    /** 특정 시점 이후(inclusive) SUCCESS 거래 유형별 누적 금액 */
    @Override
    public BigDecimal sumSuccessByTypeSince(
            Long partyId, TransactionType type, LocalDateTime since) {
        return jpaRepository.sumSuccessByTypeSince(partyId, type, since);
    }

    /** 배치 reconcile 대상 PENDING EXCHANGE ID 목록 조회 */
    @Override
    public List<Long> findPendingExchangeIdsForReconcile(int maxAttempts) {
        return jpaRepository.findIdsForReconcile(
                TransactionStatus.PENDING,
                TransactionType.EXCHANGE,
                LocalDateTime.now(),
                maxAttempts);
    }

    @Override
    public boolean existsSuccessCancelByOriginalTransactionUuid(String originalTransactionUuid) {
        return jpaRepository.existsByOriginalTransactionUuidAndTransactionTypeAndStatus(
                originalTransactionUuid, TransactionType.CANCEL, TransactionStatus.SUCCESS);
    }

    /** 가장 최근 PENDING CHARGE 1건 조회 - 중복 init 방지용 */
    @Override
    public Optional<Transaction> findLatestPendingCharge(Long partyId) {
        return jpaRepository
                .findFirstByFromParty_IdAndTransactionTypeAndStatusOrderByCreatedAtDescIdDesc(
                        partyId, TransactionType.CHARGE, TransactionStatus.PENDING);
    }

    /** 전체 기간 거래 유형별 SUCCESS 누적 금액 조회 */
    @Override
    public BigDecimal sumAllSuccessByType(Long partyId, TransactionType type) {
        return jpaRepository.sumAllSuccessByType(partyId, type);
    }

    @Override
    public boolean existsSuccessCancelFor(String originalTransactionUuid) {
        return jpaRepository.existsByOriginalTransactionUuidAndTransactionTypeAndStatus(
                originalTransactionUuid, TransactionType.CANCEL, TransactionStatus.SUCCESS);
    }

    @Override
    public Optional<Transaction> findRecoverableCancelByOriginalTransactionUuid(
            String originalTransactionUuid) {
        return jpaRepository.findByOriginalTransactionUuidAndTransactionTypeAndStatus(
                originalTransactionUuid, TransactionType.CANCEL, TransactionStatus.UNKNOWN);
    }

    @Override
    public List<Transaction> findMerchantPaymentsBetween(
            Long merchantPartyId,
            TransactionStatus status,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        return jpaRepository.findMerchantPaymentsBetween(
                merchantPartyId, status, startInclusive, endExclusive);
    }
}
