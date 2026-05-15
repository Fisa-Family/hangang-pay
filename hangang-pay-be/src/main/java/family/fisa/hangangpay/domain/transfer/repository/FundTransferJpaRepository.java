package family.fisa.hangangpay.domain.transfer.repository;

import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FundTransferJpaRepository extends JpaRepository<FundTransfer, Long> {

    Window<FundTransfer> findByPartyIdAndTransferTypeOrderByCreatedAtDescIdDesc(
            Long partyId, TransferType transferType, ScrollPosition position, Limit limit);

    /** 파티 식별자 기준 특정 월의 이체 유형별 누적 금액 조회 */
    @Query(
            "SELECT COALESCE(SUM(f.amount), 0) FROM FundTransfer f "
                    + "WHERE f.party.id = :partyId "
                    + "AND f.transferType = :transferType "
                    + "AND f.status = :status "
                    + "AND f.createdAt >= :startOfMonth "
                    + "AND f.createdAt < :startOfNextMonth")
    BigDecimal sumMonthlyAmount(
            @Param("partyId") Long partyId,
            @Param("transferType") TransferType transferType,
            @Param("status") TransferStatus status,
            @Param("startOfMonth") LocalDateTime startOfMonth,
            @Param("startOfNextMonth") LocalDateTime startOfNextMonth);
}
