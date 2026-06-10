package family.fisa.hangangpaybank.infra.mq.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitBlockchainRetryPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private RabbitBlockchainRetryPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("첫 retryable 실패는 10초 retry queue로 발행한다")
    void firstFailurePublishesTo10sRetryQueue() {
        BlockchainSyncMessage message = messageWithRetryCount(0);

        publisher.publishRetryOrDlq(message);

        BlockchainSyncMessage published = verifyPublish(RabbitMqConfig.RETRY_10S_ROUTING_KEY);
        assertThat(published.retryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번째 retryable 실패는 1분 retry queue로 발행한다")
    void secondFailurePublishesTo1mRetryQueue() {
        BlockchainSyncMessage message = messageWithRetryCount(1);

        publisher.publishRetryOrDlq(message);

        BlockchainSyncMessage published = verifyPublish(RabbitMqConfig.RETRY_1M_ROUTING_KEY);
        assertThat(published.retryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("세 번째 retryable 실패는 5분 retry queue로 발행한다")
    void thirdFailurePublishesTo5mRetryQueue() {
        BlockchainSyncMessage message = messageWithRetryCount(2);

        publisher.publishRetryOrDlq(message);

        BlockchainSyncMessage published = verifyPublish(RabbitMqConfig.RETRY_5M_ROUTING_KEY);
        assertThat(published.retryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("retry queue 시도 초과 시 최종 DLQ로 발행한다")
    void exceededRetryPublishesToFinalDlq() {
        BlockchainSyncMessage message = messageWithRetryCount(3);

        publisher.publishRetryOrDlq(message);

        ArgumentCaptor<BlockchainSyncMessage> captor =
                ArgumentCaptor.forClass(BlockchainSyncMessage.class);
        verify(rabbitTemplate)
                .convertAndSend(
                        eq(RabbitMqConfig.DLQ_EXCHANGE),
                        eq(RabbitMqConfig.DLQ_ROUTING_KEY),
                        captor.capture());
        assertThat(captor.getValue().retryCount()).isEqualTo(4);
    }

    private BlockchainSyncMessage verifyPublish(String routingKey) {
        ArgumentCaptor<BlockchainSyncMessage> captor =
                ArgumentCaptor.forClass(BlockchainSyncMessage.class);
        verify(rabbitTemplate)
                .convertAndSend(eq(RabbitMqConfig.EXCHANGE), eq(routingKey), captor.capture());
        return captor.getValue();
    }

    private BlockchainSyncMessage messageWithRetryCount(int retryCount) {
        return new BlockchainSyncMessage(
                "outbox-1",
                1L,
                10L,
                "uuid-1",
                BlockchainSyncType.PAYMENT,
                objectMapper.createObjectNode(),
                retryCount);
    }
}
