package family.fisa.hangangpay.domain.transaction.entity;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.entity.BaseEntity;
import family.fisa.hangangpay.global.exception.BusinessException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 통합 Entity.
 *
 * <p>거래 종류(transaction_type)별 유효 필드 매트릭스:
 *
 * <pre>
 *                         | CHARGE | EXCHANGE | PAYMENT | CANCEL
 * fromParty               |   ✓    |    ✓     |    ✓    |   ✓
 * toParty                 |   -    |    -     |    ✓    |   ✓
 * fromAccount             |   ✓    |    -     |    -    |   -
 * toAccount               |   -    |    ✓     |    -    |   -
 * fromWallet              |   -    |    ✓     |    ✓    |   ✓
 * toWallet                |   ✓    |    -     |    ✓    |   ✓
 * amount                  |   ✓    |    ✓     |    ✓    |   ✓
 * discountAmount          |   ✓    |    ✓     |    -    |   -
 * discountRate            |   ✓    |    ✓     |    -    |   -
 * approvalNumber          |   -    |    -     |    ✓    |   ✓
 * itemName                |   -    |    -     |    ✓    |   -
 * txHash                  |   ✓    |    ✓     |    ✓    |   ✓ (BankClient 응답에서 채움)
 * bankTransactionId       |   ✓    |    ✓     |    -    |   -  (account_ledger.id 값)
 * originalTransactionUuid |   -    |    -     |    -    |   ✓
 * </pre>
 *
 * <p>유효성은 정적 팩토리 메서드(forCharge/forExchange/forPayment/forCancel)에서 강제.
 */
@Entity
@Table(name = "transaction")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseEntity {

    /** 사건 식별자 (물리적 PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 비즈니스 거래 식별자 (프론트 생성 멱등키) */
    @Column(name = "transaction_uuid", nullable = false, unique = true, length = 36)
    private String transactionUuid;

    /** CANCEL 전용 - 원본 PAYMENT의 transaction_uuid 참조 */
    @Column(name = "original_transaction_uuid", length = 36)
    private String originalTransactionUuid;

    /** 거래 종류 (CHARGE / EXCHANGE / PAYMENT / CANCEL) */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    /** 거래 상태 (PENDING / SUCCESS / FAILED) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /** 출발 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_party_id")
    private Party fromParty;

    /** 도착 사용자 (PAYMENT/CANCEL 전용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_party_id")
    private Party toParty;

    /** 출금 계좌 (CHARGE 전용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    /** 입금 계좌 (EXCHANGE 전용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    /** 출금 지갑 (EXCHANGE/PAYMENT/CANCEL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id")
    private Wallet fromWallet;

    /** 입금 지갑 (CHARGE/PAYMENT/CANCEL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id")
    private Wallet toWallet;

    /** 거래 금액 */
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 할인 금액 (CHARGE/EXCHANGE 전용) */
    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    /** 할인율 (CHARGE/EXCHANGE 전용) */
    @Column(name = "discount_rate", precision = 5, scale = 2)
    private BigDecimal discountRate;

    /** PAYMENT 전용 - 승인번호 (APV-YYYY-NNNNNNNN) */
    @Column(name = "approval_number", unique = true, length = 50)
    private String approvalNumber;

    /** PAYMENT 전용 - 상품명 */
    @Column(name = "item_name", length = 100)
    private String itemName;

    /** 블록체인 증거 (blockchain_ledger 매칭 키, FK 없음) */
    @Column(name = "tx_hash", length = 100)
    private String txHash;

    /** 은행 거래 ID (account_ledger.id 값, FK 없음) */
    @Column(name = "bank_transaction_id", length = 100)
    private String bankTransactionId;

    /** CHARGE: 계좌 → 토큰 mint */
    public static Transaction forCharge(
            String transactionUuid,
            Party fromParty,
            Account fromAccount,
            Wallet toWallet,
            BigDecimal amount,
            BigDecimal discountAmount,
            BigDecimal discountRate) {
        return Transaction.builder()
                .transactionUuid(transactionUuid)
                .transactionType(TransactionType.CHARGE)
                .status(TransactionStatus.PENDING)
                .fromParty(fromParty)
                .fromAccount(fromAccount)
                .toWallet(toWallet)
                .amount(amount)
                .discountAmount(discountAmount)
                .discountRate(discountRate)
                .build();
    }

    /** EXCHANGE: 토큰 burn → 계좌 입금 */
    public static Transaction forExchange(
            String transactionUuid,
            Party fromParty,
            Wallet fromWallet,
            Account toAccount,
            BigDecimal amount,
            BigDecimal discountAmount,
            BigDecimal discountRate) {
        return Transaction.builder()
                .transactionUuid(transactionUuid)
                .transactionType(TransactionType.EXCHANGE)
                .status(TransactionStatus.PENDING)
                .fromParty(fromParty)
                .fromWallet(fromWallet)
                .toAccount(toAccount)
                .amount(amount)
                .discountAmount(discountAmount)
                .discountRate(discountRate)
                .build();
    }

    /** PAYMENT: 지갑 → 지갑 transfer */
    public static Transaction forPayment(
            String transactionUuid,
            Party fromParty,
            Party toParty,
            Wallet fromWallet,
            Wallet toWallet,
            BigDecimal amount,
            String approvalNumber,
            String itemName) {
        return Transaction.builder()
                .transactionUuid(transactionUuid)
                .transactionType(TransactionType.PAYMENT)
                .status(TransactionStatus.PENDING)
                .fromParty(fromParty)
                .toParty(toParty)
                .fromWallet(fromWallet)
                .toWallet(toWallet)
                .amount(amount)
                .approvalNumber(approvalNumber)
                .itemName(itemName)
                .build();
    }

    /** CANCEL: PAYMENT 역방향 transfer */
    public static Transaction forCancel(
            String transactionUuid,
            String originalTransactionUuid,
            Party fromParty,
            Party toParty,
            Wallet fromWallet,
            Wallet toWallet,
            BigDecimal amount,
            String approvalNumber) {
        return Transaction.builder()
                .transactionUuid(transactionUuid)
                .originalTransactionUuid(originalTransactionUuid)
                .transactionType(TransactionType.CANCEL)
                .status(TransactionStatus.PENDING)
                .fromParty(fromParty)
                .toParty(toParty)
                .fromWallet(fromWallet)
                .toWallet(toWallet)
                .amount(amount)
                .approvalNumber(approvalNumber)
                .build();
    }

    /** BankClient 응답을 반영해 성공 상태로 마무리 (JPA 변경감지) */
    public void completeWithBankResponse(String txHash, String bankTransactionId) {
        this.txHash = txHash;
        this.bankTransactionId = bankTransactionId;
        this.status = TransactionStatus.SUCCESS;
    }

    /** 거래 실패 마킹 (JPA 변경감지) */
    public void markFailed() {
        this.status = TransactionStatus.FAILED;
    }

    /** 트랜잭션 상태 전이 메서드 */
    public void markProcessing() { this.status = TransactionStatus.PROCESSING; }

    /** 결제 가능한 상태인지 검증 */
    public void validateExecutableBy(Long partyId) {
        if (!this.fromParty.getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }

        if (this.status != TransactionStatus.PENDING) {
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_STATUS);

        }
    }

    /** 네트워크 오류로 인한 확인 불가 상태 */
    public void markUnknown() { this.status = TransactionStatus.UNKNOWN; }
}
