package family.fisa.hangangpaybank.infra.mq.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ를 위한 설정 클래스. */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "blockchain-sync";
    public static final String QUEUE = "blockchain-sync";
    public static final String ROUTING_KEY = "blockchain.sync";
    public static final String DLQ_EXCHANGE = "blockchain-sync.dlq";
    public static final String DLQ_QUEUE = "blockchain-sync.dlq";
    public static final String DLQ_ROUTING_KEY = "blockchain-sync.dlq";
    public static final String RETRY_10S_QUEUE = "blockchain-sync.retry.10s";
    public static final String RETRY_10S_ROUTING_KEY = "blockchain.sync.retry.10s";
    public static final int RETRY_10S_TTL_MS = 10_000;
    public static final String RETRY_1M_QUEUE = "blockchain-sync.retry.1m";
    public static final String RETRY_1M_ROUTING_KEY = "blockchain.sync.retry.1m";
    public static final int RETRY_1M_TTL_MS = 60_000;
    public static final String RETRY_5M_QUEUE = "blockchain-sync.retry.5m";
    public static final String RETRY_5M_ROUTING_KEY = "blockchain.sync.retry.5m";
    public static final int RETRY_5M_TTL_MS = 300_000;

    @Bean
    DirectExchange blockchainSyncExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue blockchainSyncQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLQ_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding blockchainSyncBinding() {
        return BindingBuilder.bind(blockchainSyncQueue())
                .to(blockchainSyncExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    DirectExchange blockchainSyncDlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    Queue blockchainSyncDlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    Binding blockchainSyncDlqBinding() {
        return BindingBuilder.bind(blockchainSyncDlqQueue())
                .to(blockchainSyncDlqExchange())
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    Queue blockchainSyncRetry10sQueue() {
        return retryQueue(RETRY_10S_QUEUE, RETRY_10S_TTL_MS);
    }

    @Bean
    Binding blockchainSyncRetry10sBinding() {
        return BindingBuilder.bind(blockchainSyncRetry10sQueue())
                .to(blockchainSyncExchange())
                .with(RETRY_10S_ROUTING_KEY);
    }

    @Bean
    Queue blockchainSyncRetry1mQueue() {
        return retryQueue(RETRY_1M_QUEUE, RETRY_1M_TTL_MS);
    }

    @Bean
    Binding blockchainSyncRetry1mBinding() {
        return BindingBuilder.bind(blockchainSyncRetry1mQueue())
                .to(blockchainSyncExchange())
                .with(RETRY_1M_ROUTING_KEY);
    }

    @Bean
    Queue blockchainSyncRetry5mQueue() {
        return retryQueue(RETRY_5M_QUEUE, RETRY_5M_TTL_MS);
    }

    @Bean
    Binding blockchainSyncRetry5mBinding() {
        return BindingBuilder.bind(blockchainSyncRetry5mQueue())
                .to(blockchainSyncExchange())
                .with(RETRY_5M_ROUTING_KEY);
    }

    @Bean
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    /**
     * 예상 밖 예외로 NACK 발생 시 메시지를 최종 DLQ로 이동한다.
     *
     * <p>Spring AMQP 기본값(defaultRequeueRejected=true)은 NACK 시 원래 큐로 재투입하는데, retryable 오류(RPC 장애 등)가
     * 해소되기 전까지 같은 메시지를 무한 반복 처리하는 문제가 생긴다.
     *
     * <p>retryable 오류는 listener가 명시적으로 retry queue에 republish하고 정상 반환해 원본 메시지를 ACK한다. 이 설정은 핸들러 누락
     * 등 예상 밖 예외에 대한 안전망이다.
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    private Queue retryQueue(String queueName, int ttlMs) {
        return QueueBuilder.durable(queueName)
                .ttl(ttlMs)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }
}
