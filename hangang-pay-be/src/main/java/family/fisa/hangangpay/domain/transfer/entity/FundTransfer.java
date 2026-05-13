package family.fisa.hangangpay.domain.transfer.entity;

import family.fisa.hangangpay.domain.account.entity.Account;
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
@Table(name = "fund_transfer")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundTransfer extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 (party.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    /** 연결 은행 계좌 (account.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** 대상 블록체인 지갑 (wallet.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    /** 거래 금액 (액면가) */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    /** 할인 금액 */
    @Column(precision = 20, scale = 4)
    private BigDecimal discountAmount;

    /** 할인율 (예: 0.1 = 10%) */
    @Column(precision = 5, scale = 4)
    private BigDecimal discountRate;

    /** 거래 상태 (PENDING, SUCCESS, FAILED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    /** 거래 종류 (CHARGE, EXCHANGE) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferType transferType;
}
