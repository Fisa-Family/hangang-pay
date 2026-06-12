package family.fisa.hangangpaybank.domain.blockchainoutbox.entity;

import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ordering_key(사용자 지갑 주소)별 seq_no 발급 상태를 관리하는 테이블.
 *
 * <p>같은 ordering_key에 대해 비관적 락을 걸고 nextSeqNo를 증가시켜, blockchain_outbox의 seq_no가 사용자 단위로 중복 없이 순서대로
 * 발급되도록 보장한다.
 */
@Entity
@Table(name = "blockchain_ordering_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BlockchainOrderingState extends BaseEntity {

    /** 순서 보장 단위. payment/cancel은 사용자 지갑 주소, exchange는 지갑 주소를 정규화한 값. */
    @Id
    @Column(name = "ordering_key", nullable = false, length = 100)
    private String orderingKey;

    /** 다음에 발급할 seq_no. 발급할 때마다 1씩 증가한다. */
    @Column(name = "next_seq_no", nullable = false)
    private Long nextSeqNo;

    /** 마지막으로 완료(SUCCESS)된 seq_no. 모니터링/운영 참고용. */
    @Column(name = "last_completed_seq_no", nullable = false)
    private Long lastCompletedSeqNo;

    /** 새 ordering_key에 대한 state를 생성한다. seq_no는 1부터 시작한다. */
    public static BlockchainOrderingState start(String orderingKey) {
        return BlockchainOrderingState.builder()
                .orderingKey(orderingKey)
                .nextSeqNo(1L)
                .lastCompletedSeqNo(0L)
                .build();
    }

    /** 현재 nextSeqNo를 발급하고 1 증가시킨다. 반드시 비관적 락 컨텍스트 안에서 호출해야 한다. */
    public Long issueNextSeqNo() {
        Long issued = nextSeqNo;
        nextSeqNo++;
        return issued;
    }
}
