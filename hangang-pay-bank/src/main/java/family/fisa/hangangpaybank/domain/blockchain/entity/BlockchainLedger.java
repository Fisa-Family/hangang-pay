package family.fisa.hangangpaybank.domain.blockchain.entity;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Entity
@Table(name = "blockchain_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BlockchainLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 플랫폼이 발행한 거래 식별자
    @Column(name = "idempotent_key", nullable = false, unique = true, length = 36)
    private String idempotentKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "tx_hash", length = 100)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BlockchainTxStatus status;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public static BlockchainLedger of(
            Institution institution, BlockchainTxStatus status, String idempotentKey) {
        return BlockchainLedger.builder()
                .institution(institution)
                .status(status)
                .idempotentKey(idempotentKey)
                .build();
    }

    public void markSubmitted(String txHash) {
        this.status = BlockchainTxStatus.SUBMITTED;
        this.txHash = txHash;
    }

    public void markSuccess(TransactionReceipt receipt) {
        this.status = BlockchainTxStatus.SUCCESS;
        this.txHash = receipt.getTransactionHash();
        this.blockNumber =
                receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValueExact() : null;
        this.confirmedAt = LocalDateTime.now();
    }

    /** 온체인 AlreadyProcessed: 이전 시도가 이미 확정된 경우. txHash는 불명이므로 null 유지. */
    public void markAlreadyProcessed() {
        this.status = BlockchainTxStatus.SUCCESS;
        this.confirmedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = BlockchainTxStatus.FAILED;
    }

    /** 컨트랙트 실패 후 FAILED 상태로 전환 */
    public void fail() {
        this.status = BlockchainTxStatus.FAILED;
    }
}
