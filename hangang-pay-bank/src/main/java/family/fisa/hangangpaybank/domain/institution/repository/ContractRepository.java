package family.fisa.hangangpaybank.domain.institution.repository;

import family.fisa.hangangpaybank.domain.institution.entity.Contract;
import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByInstitutionIdAndName(Long institutionId, ContractType name);

    Optional<Contract> findByInstitutionInstitutionCodeAndName(
            String institutionCode, ContractType name);

    @EntityGraph(attributePaths = "institution")
    Optional<Contract> findFirstByNameOrderByIdAsc(ContractType name);

    List<Contract> findAllByName(ContractType name);

    List<Contract> findAllByNameIn(Collection<ContractType> names);
}
