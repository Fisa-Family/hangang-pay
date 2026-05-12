package family.fisa.hangangpay.domain.institution.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "bank_wallet")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankWallet extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 은행 (institution.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /** 지갑 주소 */
    @Column(nullable = false)
    private String walletAddress;

    /** 잔액 */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance;

    /** 암호화된 개인키 */
    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey;
}