package family.fisa.hangangpay.domain.blockchain.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blockchain_tx")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockchainTx extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 참조 대상 ID (reference_type에 따라 가리키는 테이블이 달라짐) */
    @Column(nullable = false)
    private Long referenceId;

    /** 참조 종류 (FUND_TRANSFER, PAYMENT, PAYMENT_CANCELLATION) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceType referenceType;

    /** 트랜잭션 해시 */
    private String txHash;

    /** 상태 (PENDING, CONFIRMED, FAILED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockchainTxStatus status;
}
