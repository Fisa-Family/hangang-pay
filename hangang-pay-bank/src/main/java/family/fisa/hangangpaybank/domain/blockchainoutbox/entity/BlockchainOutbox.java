package family.fisa.hangangpaybank.domain.blockchainoutbox.entity;

import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "blockchain_outbox",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_blockchain_outbox_ordering_seq",
                        columnNames = {"ordering_key", "seq_no"}),
        indexes = {
            @Index(
                    name = "idx_blockchain_outbox_status_ordering_seq",
                    columnList = "status, ordering_key, seq_no"),
            @Index(name = "idx_blockchain_outbox_ledger_id", columnList = "blockchain_ledger_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BlockchainOutbox extends BaseEntity {

    static final int MAX_RETRY = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blockchain_ledger_id", nullable = false)
    private Long blockchainLedgerId;

    @Column(name = "transaction_uuid", nullable = false, length = 36)
    private String transactionUuid;

    @Column(name = "ordering_key", nullable = false, length = 100)
    private String orderingKey;

    @Column(name = "seq_no", nullable = false)
    private Long seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private BlockchainSyncType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BlockchainOutboxStatus status;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    public String getMessageId() {
        return "outbox-" + this.id;
    }

    public void markSent() {
        this.status = BlockchainOutboxStatus.SENT;
    }

    public void reopenForReconcile() {
        this.status = BlockchainOutboxStatus.NEW;
        this.retryCount = 0;
    }

    /** outbox의 발행 시도 횟수를 증가시킨다. MAX_RETRY 횟수에 도달하면 해당 outbox를 FAILED 상태로 전환한다. */
    public void incrementRetryOrFail() {
        this.retryCount++;
        if (this.retryCount >= MAX_RETRY) {
            this.status = BlockchainOutboxStatus.FAILED;
        }
    }
}
