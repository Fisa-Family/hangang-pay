package family.fisa.hangangpay.domain.transfer.repository;

import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundTransferJpaRepository extends JpaRepository<FundTransfer, Long> {
    Window<FundTransfer> findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc(
            Long partyId, TransferType transferType, ScrollPosition position, Limit limit);
}
