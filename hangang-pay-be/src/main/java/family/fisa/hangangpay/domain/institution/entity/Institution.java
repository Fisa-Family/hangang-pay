package family.fisa.hangangpay.domain.institution.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "institution")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Institution extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기관 코드 */
    @Column(nullable = false, unique = true)
    private String institutionCode;

    /** 은행명(기관명) */
    @Column(nullable = false)
    private String institutionName;

    /** 기관 대표 계좌번호 */
    private String accountNumber;

    /** 기관 대표 지갑 주소 */
    private String walletAddress;

    /** 암호화된 개인키 */
    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    /** Besu 노드 enode URL */
    private String enodeUrl;

    /** RPC 엔드포인트 */
    private String rpcEndpoint;
}