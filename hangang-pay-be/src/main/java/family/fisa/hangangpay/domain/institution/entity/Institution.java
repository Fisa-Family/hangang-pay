package family.fisa.hangangpay.domain.institution.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "institution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Institution extends BaseEntity {

    @Id private Long id;

    @Column(name = "institution_code", nullable = false, unique = true, length = 20)
    private String institutionCode;

    @Column(name = "institution_name", nullable = false, length = 100)
    private String institutionName;
}
