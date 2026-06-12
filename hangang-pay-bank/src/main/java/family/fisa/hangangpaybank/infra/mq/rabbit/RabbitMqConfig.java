package family.fisa.hangangpaybank.infra.mq.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ를 위한 설정 클래스. */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "blockchain-sync-shard";
    public static final String DLQ_EXCHANGE = "blockchain-sync.dlq";
    public static final String DLQ_QUEUE = "blockchain-sync.dlq";
    public static final String DLQ_ROUTING_KEY = "blockchain-sync.dlq";

    @Value("${blockchain.sync.shard-count:4}")
    private int shardCount;

    @Value("${blockchain.sync.queue-prefix:blockchain-sync}")
    private String queuePrefix;

    /**
     * x-modulus-hash exchange와 shard queue N개를 한 번에 선언한다.
     *
     * <p>같은 ordering_key(routing key)는 hash(orderingKey) % total_weight 로 항상 같은 shard queue로 라우팅되어,
     * 사용자 내 순서를 유지하면서 사용자 간 병렬 처리가 가능하다. (requires: rabbitmq_consistent_hash_exchange plugin)
     *
     * <p>각 shard queue는 Single Active Consumer를 활성화해 queue 내부에서 동시 처리가 일어나지 않도록 보장한다.
     */
    @Bean
    Declarables blockchainSyncDeclarables() {
        List<Declarable> declarables = new ArrayList<>();

        CustomExchange exchange = new CustomExchange(EXCHANGE, "x-modulus-hash", true, false);
        declarables.add(exchange);

        for (int i = 0; i < shardCount; i++) {
            String queueName = queuePrefix + "." + i;
            Queue queue =
                    QueueBuilder.durable(queueName)
                            .singleActiveConsumer()
                            .deadLetterExchange(DLQ_EXCHANGE)
                            .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                            .build();
            // binding key = weight. "1"은 각 queue가 동등한 비율로 메시지를 수신함을 의미한다.
            Binding binding = BindingBuilder.bind(queue).to(exchange).with("1").noargs();
            declarables.add(queue);
            declarables.add(binding);
        }

        return new Declarables(declarables);
    }

    /** 리스너가 구독할 shard queue 이름 목록. */
    @Bean
    List<String> blockchainSyncShardQueueNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            names.add(queuePrefix + "." + i);
        }
        return names;
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

    /**
     * NACK 발생 시 메시지를 즉시 DLQ로 이동한다.
     *
     * <p>Spring AMQP 기본값(defaultRequeueRejected=true)은 NACK 시 원래 큐로 재투입하는데, retryable 오류(RPC 장애 등)가
     * 해소되기 전까지 같은 메시지를 무한 반복 처리하는 문제가 생긴다.
     *
     * <p>로컬 재시도(RetryInterceptor)를 두지 않는 이유: RPC 장애는 수십 초~수 분 단위 장애라 컨슈머 스레드를 블로킹하며 재시도해도 회복 가능성이
     * 낮고, concurrency=1 구조에서 재시도 대기 중 다른 메시지를 처리하지 못하는 비용이 크다. DLQ에서 운영팀이 상황 확인 후 수동 재투입하는 방식이 더
     * 안전하다.
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
}
