package family.fisa.hangangpay.domain.payment.entity;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_party_id", nullable = false)
    private Party payerParty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_party_id", nullable = false)
    private Party payeeParty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_wallet_id", nullable = false)
    private Wallet payerWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_wallet_id", nullable = false)
    private Wallet payeeWallet;

    private String itemName;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    private String approvalNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
