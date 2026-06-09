package family.fisa.hangangpaybank.infra.mq.rabbit;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitBlockchainRetryPublisher {

    private static final int MAX_RETRY_QUEUE_ATTEMPTS = 3;

    private final RabbitTemplate rabbitTemplate;

    public void publishRetryOrDlq(BlockchainSyncMessage message) {
        int nextRetryCount = message.retryCount() + 1;
        BlockchainSyncMessage retryMessage = message.withRetryCount(nextRetryCount);

        if (nextRetryCount > MAX_RETRY_QUEUE_ATTEMPTS) {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.DLQ_EXCHANGE, RabbitMqConfig.DLQ_ROUTING_KEY, retryMessage);
            log.warn(
                    "[retry] 최종 DLQ 이동. uuid={}, retryCount={}",
                    retryMessage.transactionUuid(),
                    retryMessage.retryCount());
            return;
        }

        String routingKey = retryRoutingKey(nextRetryCount);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, retryMessage);
        log.warn(
                "[retry] retry queue 발행. uuid={}, retryCount={}, routingKey={}",
                retryMessage.transactionUuid(),
                retryMessage.retryCount(),
                routingKey);
    }

    private String retryRoutingKey(int retryCount) {
        return switch (retryCount) {
            case 1 -> RabbitMqConfig.RETRY_10S_ROUTING_KEY;
            case 2 -> RabbitMqConfig.RETRY_1M_ROUTING_KEY;
            case 3 -> RabbitMqConfig.RETRY_5M_ROUTING_KEY;
            default -> throw new IllegalArgumentException("Unsupported retryCount=" + retryCount);
        };
    }
}
