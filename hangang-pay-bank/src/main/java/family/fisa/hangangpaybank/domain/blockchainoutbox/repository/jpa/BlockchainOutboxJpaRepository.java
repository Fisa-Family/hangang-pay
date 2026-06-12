package family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlockchainOutboxJpaRepository extends JpaRepository<BlockchainOutbox, Long> {

    List<BlockchainOutbox> findAllByStatus(BlockchainOutboxStatus status);

    /**
     * 같은 ordering_key에서 선행 seq가 모두 완료된 경우에만 해당 outbox를 publish 후보로 반환한다.
     *
     * <p>같은 ordering_key에 미완료(ledger status != completedStatus) 선행 seq가 하나라도 남아 있으면 후행 seq는 조회 대상에서
     * 제외된다. 이를 통해 사용자 단위 블록체인 반영 순서를 보장한다.
     *
     * <p>실패(FAILED) ledger도 미완료로 간주하므로, 선행 seq가 실패한 경우 운영 판단 전까지 후행 seq는 차단된다.
     */
    @Query(
            """
            select o
            from BlockchainOutbox o
            where o.status = :newStatus
              and not exists (
                  select 1
                  from BlockchainOutbox prev, BlockchainLedger prevLedger
                  where prevLedger.id = prev.blockchainLedgerId
                    and prev.orderingKey = o.orderingKey
                    and prev.seqNo < o.seqNo
                    and prevLedger.status <> :completedStatus
              )
            order by o.seqNo asc
            """)
    List<BlockchainOutbox> findPublishableNew(
            BlockchainOutboxStatus newStatus,
            BlockchainTxStatus completedStatus,
            Pageable pageable);

    Optional<BlockchainOutbox> findByBlockchainLedgerId(Long blockchainLedgerId);

    List<BlockchainOutbox> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            BlockchainOutboxStatus status, LocalDateTime updatedBefore, Pageable pageable);
}
