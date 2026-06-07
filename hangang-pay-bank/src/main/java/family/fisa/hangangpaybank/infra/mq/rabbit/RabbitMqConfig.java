package family.fisa.hangangpaybank.infra.mq.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
