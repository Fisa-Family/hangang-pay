package family.fisa.hangangpaybank.domain.blockchainoutbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncMessagePublisher;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainOutboxPublisherSchedulerTest {

    @Mock private BlockchainOutboxRepository blockchainOutboxRepository;
    @Mock private BlockchainSyncMessagePublisher publisher;

    private BlockchainOutboxPublisherScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new BlockchainOutboxPublisherScheduler(
                        blockchainOutboxRepository, publisher, new ObjectMapper());
    }

    @Test
    @DisplayName("NEW 상태 outbox를 publisher로 전달하고 SENT로 전환한다")
    void publishesPendingOutboxAndMarksSent() throws Exception {
        BlockchainOutbox outbox = newOutbox(1L);
        given(blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW))
                .willReturn(List.of(outbox));

        scheduler.publishPending();

        verify(publisher).publish(any());
        assertThat(outbox.getStatus()).isEqualTo(BlockchainOutboxStatus.SENT);
    }

    @Test
    @DisplayName("publish 실패 시 retryCount를 증가시킨다")
    void incrementsRetryCountOnPublishFailure() throws Exception {
        BlockchainOutbox outbox = newOutbox(2L);
        given(blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW))
                .willReturn(List.of(outbox));
        willThrow(new RuntimeException("MQ unavailable")).given(publisher).publish(any());

        scheduler.publishPending();

        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(BlockchainOutboxStatus.NEW);
    }

    @Test
    @DisplayName("publish 실패가 MAX_RETRY에 도달하면 FAILED로 전환한다")
    void marksFailedWhenMaxRetryReached() throws Exception {
        BlockchainOutbox outbox = newOutbox(3L);
        given(blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW))
                .willReturn(List.of(outbox));
        willThrow(new RuntimeException("MQ unavailable")).given(publisher).publish(any());

        for (int i = 0; i < 3; i++) {
            scheduler.publishPending();
        }

        assertThat(outbox.getStatus()).isEqualTo(BlockchainOutboxStatus.FAILED);
    }

    @Test
    @DisplayName("NEW 상태 outbox가 없으면 publisher를 호출하지 않는다")
    void doesNotPublishWhenNoPendingOutbox() {
        given(blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW))
                .willReturn(List.of());

        scheduler.publishPending();

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("publish 시 outboxId에서 파생된 messageId로 메시지를 전달한다")
    void publishesMessageWithStableMessageId() throws Exception {
        BlockchainOutbox outbox = newOutbox(42L);
        given(blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW))
                .willReturn(List.of(outbox));

        scheduler.publishPending();

        ArgumentCaptor<family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage>
                captor =
                        ArgumentCaptor.forClass(
                                family.fisa.hangangpaybank.domain.blockchainoutbox.dto
                                        .BlockchainSyncMessage.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo("outbox-42");
        assertThat(captor.getValue().transactionUuid()).isEqualTo("uuid-42");
    }

    private static BlockchainOutbox newOutbox(Long id) {
        return BlockchainOutbox.builder()
                .id(id)
                .blockchainLedgerId(100L)
                .transactionUuid("uuid-" + id)
                .type(BlockchainSyncType.PAYMENT)
                .status(BlockchainOutboxStatus.NEW)
                .payload(
                        "{\"fromWalletAddress\":\"0xA\",\"toWalletAddress\":\"0xB\",\"amount\":100}")
                .retryCount(0)
                .build();
    }
}
