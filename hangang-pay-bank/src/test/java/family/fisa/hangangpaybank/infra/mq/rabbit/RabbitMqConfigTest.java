package family.fisa.hangangpaybank.infra.mq.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class RabbitMqConfigTest {

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    @DisplayName("10초 retry queue는 TTL 후 main queue로 dead-letter 된다")
    void retry10sQueueDeadLettersBackToMainQueue() {
        Queue queue = config.blockchainSyncRetry10sQueue();

        assertRetryQueue(queue, RabbitMqConfig.RETRY_10S_QUEUE, RabbitMqConfig.RETRY_10S_TTL_MS);
    }

    @Test
    @DisplayName("1분 retry queue는 TTL 후 main queue로 dead-letter 된다")
    void retry1mQueueDeadLettersBackToMainQueue() {
        Queue queue = config.blockchainSyncRetry1mQueue();

        assertRetryQueue(queue, RabbitMqConfig.RETRY_1M_QUEUE, RabbitMqConfig.RETRY_1M_TTL_MS);
    }

    @Test
    @DisplayName("5분 retry queue는 TTL 후 main queue로 dead-letter 된다")
    void retry5mQueueDeadLettersBackToMainQueue() {
        Queue queue = config.blockchainSyncRetry5mQueue();

        assertRetryQueue(queue, RabbitMqConfig.RETRY_5M_QUEUE, RabbitMqConfig.RETRY_5M_TTL_MS);
    }

    private void assertRetryQueue(Queue queue, String queueName, int ttlMs) {
        assertThat(queue.getName()).isEqualTo(queueName);
        assertThat(queue.getArguments())
                .containsEntry("x-message-ttl", ttlMs)
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.ROUTING_KEY);
    }
}
