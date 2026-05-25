package family.fisa.hangangpaybank.domain.blockchain.entity;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @Column(
            name = "idempotent_key",
            nullable = true,
            unique = true,
            length = 36) // 일시적 nullable = true 추후 수정예정
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
}
