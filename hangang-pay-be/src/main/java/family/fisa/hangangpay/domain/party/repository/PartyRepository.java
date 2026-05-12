package family.fisa.hangangpay.domain.party.repository;

import family.fisa.hangangpay.domain.party.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<Party, Long> {
}
