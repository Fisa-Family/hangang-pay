package family.fisa.hangangpaybank.infra.mq.rabbit;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ를 이용한 BlockchainSyncMessagePublisher 구현체. Outbox 패턴으로 저장된 블록체인 동기화 메시지를 지정된 Exchange와
 * Routing Key를 통해 발행한다.
 */
@Component
@ConditionalOnProperty(name = "mq.provider", havingValue = "rabbit")
@RequiredArgsConstructor
public class RabbitBlockchainSyncMessagePublisher implements BlockchainSyncMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    /** 블록체인 동기화 메시지를 RabbitMQ로 발행한다. */
    @Override
    public void publish(BlockchainSyncMessage message) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY, message);
    }
}
