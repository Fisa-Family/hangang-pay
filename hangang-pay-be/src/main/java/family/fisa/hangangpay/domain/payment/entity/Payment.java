package family.fisa.hangangpay.domain.payment.entity;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 결제자 (party.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_party_id", nullable = false)
    private Party payerParty;

    /** 수취자 (party.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_party_id", nullable = false)
    private Party payeeParty;

    /** 결제자 지갑 (wallet.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_wallet_id", nullable = false)
    private Wallet payerWallet;

    /** 수취자 지갑 (wallet.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_wallet_id", nullable = false)
    private Wallet payeeWallet;

    /** 상품명 */
    private String itemName;

    /** 결제 금액 */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    /** 승인번호 (APV-YYYY-NNNNNNNN, payment.id 8자리 zero padding) */
    private String approvalNumber;

    /** 결제 상태 (PENDING, SUCCESS, FAILED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
}
