package family.fisa.hangangpay.domain.transfer.repository;

import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundTransferRepository extends JpaRepository<FundTransfer, Long> {
}
