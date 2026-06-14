package family.fisa.hangangpay.domain.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionRepositoryImplTest {

    @Autowired TransactionRepository transactionRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("가맹점 결제 이력: PAYMENT(toParty=merchant)와 CANCEL(fromParty=merchant)을 최신순으로 조회")
    void findPaymentTransactionsByMerchantPartyId_filtersPaymentAndCancelDirections() {
        Party user = persistParty(PartyType.USER);
        Party otherUser = persistParty(PartyType.USER);
        Party merchant = persistParty(PartyType.MERCHANT);
        Party otherMerchant = persistParty(PartyType.MERCHANT);

        Transaction oldPayment =
                persistTransaction(
                        "payment-old",
                        TransactionType.PAYMENT,
                        user,
                        merchant,
                        "APV-2026-00000001",
                        LocalDateTime.of(2026, 5, 1, 10, 0));
        Transaction latestCancel =
                persistTransaction(
                        "cancel-latest",
                        TransactionType.CANCEL,
                        merchant,
                        otherUser,
                        "APV-2026-00000002",
                        LocalDateTime.of(2026, 5, 2, 10, 0));
        persistTransaction(
                "payment-other-merchant",
                TransactionType.PAYMENT,
                user,
                otherMerchant,
                "APV-2026-00000003",
                LocalDateTime.of(2026, 5, 3, 10, 0));
        persistTransaction(
                "cancel-other-merchant",
                TransactionType.CANCEL,
                otherMerchant,
                user,
                "APV-2026-00000004",
                LocalDateTime.of(2026, 5, 4, 10, 0));

        entityManager.flush();
        entityManager.clear();

        Window<Transaction> result =
                transactionRepository.findPaymentTransactionsByMerchantPartyId(
                        merchant.getId(),
                        TransactionStatus.SUCCESS,
                        ScrollPosition.keyset(),
                        Limit.of(10));

        assertThat(result.hasNext()).isFalse();
        assertThat(result.getContent())
                .extracting(Transaction::getTransactionUuid)
                .containsExactly(
                        latestCancel.getTransactionUuid(), oldPayment.getTransactionUuid());
    }

    @Test
    @DisplayName("가맹점 결제 이력: createdAt DESC, id DESC 기준으로 다음 cursor 이후를 조회")
    void findPaymentTransactionsByMerchantPartyId_appliesKeysetCursor() {
        Party user = persistParty(PartyType.USER);
        Party merchant = persistParty(PartyType.MERCHANT);
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 5, 1, 10, 0);

        Transaction first =
                persistTransaction(
                        "payment-first",
                        TransactionType.PAYMENT,
                        user,
                        merchant,
                        "APV-2026-00000001",
                        sameCreatedAt);
        Transaction second =
                persistTransaction(
                        "payment-second",
                        TransactionType.PAYMENT,
                        user,
                        merchant,
                        "APV-2026-00000002",
                        sameCreatedAt);
        Transaction third =
                persistTransaction(
                        "payment-third",
                        TransactionType.PAYMENT,
                        user,
                        merchant,
                        "APV-2026-00000003",
                        sameCreatedAt);

        entityManager.flush();
        entityManager.clear();

        Window<Transaction> firstPage =
                transactionRepository.findPaymentTransactionsByMerchantPartyId(
                        merchant.getId(),
                        TransactionStatus.SUCCESS,
                        ScrollPosition.keyset(),
                        Limit.of(2));

        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent())
                .extracting(Transaction::getTransactionUuid)
                .containsExactly(third.getTransactionUuid(), second.getTransactionUuid());

        Window<Transaction> secondPage =
                transactionRepository.findPaymentTransactionsByMerchantPartyId(
                        merchant.getId(),
                        TransactionStatus.SUCCESS,
                        firstPage.positionAt(1),
                        Limit.of(2));

        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.getContent())
                .extracting(Transaction::getTransactionUuid)
                .containsExactly(first.getTransactionUuid());
    }

    @Test
    @DisplayName("가맹점 대시보드 집계: 기간 내 SUCCESS PAYMENT(toParty=merchant)만 합계와 건수에 포함")
    void aggregateMerchantPaymentDashboard_filtersSuccessPaymentsToMerchantWithinPeriod() {
        Party user = persistParty(PartyType.USER);
        Party merchant = persistParty(PartyType.MERCHANT);
        Party otherMerchant = persistParty(PartyType.MERCHANT);

        LocalDateTime start = LocalDateTime.of(2026, 5, 27, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 28, 0, 0);

        persistTransaction(
                "included-1",
                TransactionType.PAYMENT,
                TransactionStatus.SUCCESS,
                user,
                merchant,
                new BigDecimal("100000"),
                "APV-2026-00000001",
                LocalDateTime.of(2026, 5, 27, 10, 0));
        persistTransaction(
                "included-2",
                TransactionType.PAYMENT,
                TransactionStatus.SUCCESS,
                user,
                merchant,
                new BigDecimal("150000"),
                "APV-2026-00000002",
                LocalDateTime.of(2026, 5, 27, 23, 59));
        persistTransaction(
                "before-period",
                TransactionType.PAYMENT,
                TransactionStatus.SUCCESS,
                user,
                merchant,
                new BigDecimal("999999"),
                "APV-2026-00000003",
                LocalDateTime.of(2026, 5, 26, 23, 59));
        persistTransaction(
                "end-exclusive",
                TransactionType.PAYMENT,
                TransactionStatus.SUCCESS,
                user,
                merchant,
                new BigDecimal("999999"),
                "APV-2026-00000004",
                LocalDateTime.of(2026, 5, 28, 0, 0));
        persistTransaction(
                "failed-payment",
                TransactionType.PAYMENT,
                TransactionStatus.FAILED,
                user,
                merchant,
                new BigDecimal("999999"),
                "APV-2026-00000005",
                LocalDateTime.of(2026, 5, 27, 12, 0));
        persistTransaction(
                "cancel-excluded",
                TransactionType.CANCEL,
                TransactionStatus.SUCCESS,
                merchant,
                user,
                new BigDecimal("999999"),
                "APV-2026-00000006",
                LocalDateTime.of(2026, 5, 27, 13, 0));
        persistTransaction(
                "other-merchant",
                TransactionType.PAYMENT,
                TransactionStatus.SUCCESS,
                user,
                otherMerchant,
                new BigDecimal("999999"),
                "APV-2026-00000007",
                LocalDateTime.of(2026, 5, 27, 14, 0));

        entityManager.flush();
        entityManager.clear();

        List<Transaction> payments =
                transactionRepository.findMerchantPaymentsBetween(
                        merchant.getId(), TransactionStatus.SUCCESS, start, end);

        BigDecimal amount =
                payments.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(amount).isEqualByComparingTo(new BigDecimal("250000"));
        assertThat(payments.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("성공한 취소 거래가 원거래 UUID를 참조하면 true를 반환")
    void existsSuccessCancelByOriginalTransactionUuid_returnsTrueOnlyForSuccessCancel() {
        Party user = persistParty(PartyType.USER);
        Party merchant = persistParty(PartyType.MERCHANT);

        Transaction payment =
                persistTransaction(
                        "payment-original",
                        TransactionType.PAYMENT,
                        user,
                        merchant,
                        "APV-2026-00000001",
                        LocalDateTime.of(2026, 5, 1, 10, 0));
        persistCancelTransaction(
                "cancel-success",
                payment.getTransactionUuid(),
                merchant,
                user,
                TransactionStatus.SUCCESS);
        persistCancelTransaction(
                "cancel-failed-other", "payment-other", merchant, user, TransactionStatus.FAILED);

        entityManager.flush();
        entityManager.clear();

        assertThat(
                        transactionRepository.existsSuccessCancelByOriginalTransactionUuid(
                                payment.getTransactionUuid()))
                .isTrue();
        assertThat(
                        transactionRepository.existsSuccessCancelByOriginalTransactionUuid(
                                "payment-other"))
                .isFalse();
    }

    @Test
    @DisplayName("만료 처리: PAYMENT + PENDING + threshold 이전만 EXPIRED, PROCESSING/SUCCESS/타 타입은 제외")
    void expireStalePendingPaymentIntents_onlyPaymentPendingBeforeThreshold() {
        Party user = persistParty(PartyType.USER);
        Party merchant = persistParty(PartyType.MERCHANT);
        LocalDateTime threshold = LocalDateTime.of(2026, 6, 12, 10, 10);
        LocalDateTime now = LocalDateTime.of(2026, 6, 12, 10, 20);
        LocalDateTime stale = LocalDateTime.of(2026, 6, 12, 10, 0);

        // 대상: PAYMENT + PENDING + threshold 이전
        persistTransaction(
                "stale-payment-pending",
                TransactionType.PAYMENT,
                TransactionStatus.PENDING,
                user,
                merchant,
                new BigDecimal("10000"),
                null,
                stale);
        // 제외: threshold 이후 PENDING
        persistTransaction(
                "fresh-payment-pending",
                TransactionType.PAYMENT,
                TransactionStatus.PENDING,
                user,
                merchant,
                new BigDecimal("10000"),
                null,
                threshold);
        // 제외: PROCESSING (레이스 핵심 - 진행 중인 결제는 절대 만료시키지 않는다)
        persistTransaction(
                "stale-payment-processing",
                TransactionType.PAYMENT,
                TransactionStatus.PROCESSING,
                user,
                merchant,
                new BigDecimal("10000"),
                null,
                stale);
        // 제외: SUCCESS
        persistTransaction(
                "stale-payment-success",
                TransactionType.PAYMENT,
                TransactionStatus.SUCCESS,
                user,
                merchant,
                new BigDecimal("10000"),
                "APV-2026-00000011",
                stale);
        // 제외: 다른 타입(CHARGE)
        persistTransaction(
                "stale-charge-pending",
                TransactionType.CHARGE,
                TransactionStatus.PENDING,
                user,
                null,
                new BigDecimal("10000"),
                null,
                stale);

        entityManager.flush();
        entityManager.clear();

        int affected = transactionRepository.expireStalePendingPaymentIntents(threshold, now);

        entityManager.clear();

        assertThat(affected).isEqualTo(1);
        assertThat(status("stale-payment-pending")).isEqualTo(TransactionStatus.EXPIRED);
        assertThat(status("fresh-payment-pending")).isEqualTo(TransactionStatus.PENDING);
        assertThat(status("stale-payment-processing")).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(status("stale-payment-success")).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(status("stale-charge-pending")).isEqualTo(TransactionStatus.PENDING);
    }

    private TransactionStatus status(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .orElseThrow()
                .getStatus();
    }

    private Party persistParty(PartyType partyType) {
        Party party = Party.builder().partyType(partyType).build();
        entityManager.persist(party);
        return party;
    }

    private Transaction persistTransaction(
            String transactionUuid,
            TransactionType transactionType,
            TransactionStatus status,
            Party fromParty,
            Party toParty,
            BigDecimal amount,
            String approvalNumber,
            LocalDateTime createdAt) {
        Transaction transaction =
                Transaction.builder()
                        .transactionUuid(transactionUuid)
                        .transactionType(transactionType)
                        .status(status)
                        .fromParty(fromParty)
                        .toParty(toParty)
                        .amount(amount)
                        .approvalNumber(approvalNumber)
                        .build();

        entityManager.persist(transaction);
        entityManager.flush();
        entityManager
                .createQuery(
                        "UPDATE Transaction t "
                                + "SET t.createdAt = :createdAt, t.updatedAt = :createdAt "
                                + "WHERE t.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", transaction.getId())
                .executeUpdate();

        return transaction;
    }

    private Transaction persistTransaction(
            String transactionUuid,
            TransactionType transactionType,
            Party fromParty,
            Party toParty,
            String approvalNumber,
            LocalDateTime createdAt) {
        return persistTransaction(
                transactionUuid,
                transactionType,
                TransactionStatus.SUCCESS,
                fromParty,
                toParty,
                new BigDecimal("10000"),
                approvalNumber,
                createdAt);
    }

    private Transaction persistCancelTransaction(
            String transactionUuid,
            String originalTransactionUuid,
            Party fromParty,
            Party toParty,
            TransactionStatus status) {
        Transaction transaction =
                Transaction.builder()
                        .transactionUuid(transactionUuid)
                        .originalTransactionUuid(originalTransactionUuid)
                        .transactionType(TransactionType.CANCEL)
                        .status(status)
                        .fromParty(fromParty)
                        .toParty(toParty)
                        .amount(new BigDecimal("10000"))
                        .approvalNumber("APV-2026-" + transactionUuid)
                        .build();

        entityManager.persist(transaction);
        return transaction;
    }
}
