package family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOrderingState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockchainOrderingStateJpaRepository
        extends JpaRepository<BlockchainOrderingState, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BlockchainOrderingState s where s.orderingKey = :orderingKey")
    Optional<BlockchainOrderingState> findByOrderingKeyWithLock(
            @Param("orderingKey") String orderingKey);
}
