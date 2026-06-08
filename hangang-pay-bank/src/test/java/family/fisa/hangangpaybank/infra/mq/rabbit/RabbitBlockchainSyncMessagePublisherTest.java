package family.fisa.hangangpaybank.infra.mq.rabbit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitBlockchainSyncMessagePublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private RabbitBlockchainSyncMessagePublisher publisher;

    @Test
    @DisplayName("publish 시 blockchain-sync exchange와 blockchain.sync routing key로 전달한다")
    void publishSendsToCorrectExchangeAndRoutingKey() {
        BlockchainSyncMessage message =
                new BlockchainSyncMessage(
                        "outbox-1",
                        1L,
                        10L,
                        "uuid-1",
                        BlockchainSyncType.PAYMENT,
                        new ObjectMapper().createObjectNode());

        publisher.publish(message);

        verify(rabbitTemplate)
                .convertAndSend(
                        eq(RabbitMqConfig.EXCHANGE), eq(RabbitMqConfig.ROUTING_KEY), eq(message));
    }
}
