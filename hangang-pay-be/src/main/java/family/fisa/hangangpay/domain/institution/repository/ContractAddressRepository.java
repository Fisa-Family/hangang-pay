package family.fisa.hangangpay.domain.institution.repository;

import family.fisa.hangangpay.domain.institution.entity.ContractAddress;
import family.fisa.hangangpay.domain.institution.entity.ContractType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractAddressRepository extends JpaRepository<ContractAddress, Long> {

    Optional<ContractAddress> findByInstitutionIdAndName(Long institutionId, ContractType name);
}
