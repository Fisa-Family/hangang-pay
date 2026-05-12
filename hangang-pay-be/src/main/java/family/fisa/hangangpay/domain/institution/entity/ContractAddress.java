package family.fisa.hangangpay.domain.institution.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contract_address")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractAddress extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 배포 기관 (institution.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /** 컨트랙트 종류 (CBDC, DEPOSIT_TOKEN, CONTRACT) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType name;

    /** 컨트랙트 주소 (0x + 40자, 총 42자) */
    @Column(nullable = false, length = 42)
    private String address;
}