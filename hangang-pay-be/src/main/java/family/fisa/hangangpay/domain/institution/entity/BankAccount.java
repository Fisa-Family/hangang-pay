package family.fisa.hangangpay.domain.institution.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_account")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 은행 (institution.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /** 계좌번호 */
    @Column(nullable = false)
    private String accountNumber;

    /** 잔액 */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance;

    /** 잔액 차감 */
    public void deductBalance(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    /** 잔액 복원 */
    public void restoreBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
