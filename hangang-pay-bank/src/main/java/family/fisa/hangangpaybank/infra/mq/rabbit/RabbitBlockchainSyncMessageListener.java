package family.fisa.hangangpaybank.infra.mq.rabbit;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncHandler;
import family.fisa.hangangpaybank.domain.blockchainoutbox.service.BlockchainSyncHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * blockchain-sync 큐를 구독하는 RabbitMQ 컨슈머.
 *
 * <p>단일 signer 안전성을 위해 concurrency = "1"로 고정한다. 동시에 여러 트랜잭션을 submit하면 nonce 충돌이 발생할 수 있다.
 *
 * <p>ACK/NACK 정책: - 핸들러가 정상 반환하면 Spring AMQP가 자동 ACK. - 핸들러에서 예외가 전파되면 NACK → DLQ로 이동. (retryable
 * BusinessException만 전파됨. non-retryable은 processor 내부에서 처리 후 정상 반환.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitBlockchainSyncMessageListener {

    private final BlockchainSyncHandlerRegistry registry;

    @RabbitListener(queues = RabbitMqConfig.QUEUE, concurrency = "1")
    public void onMessage(BlockchainSyncMessage message) {
        log.info("[listener] 메시지 수신. type={}, uuid={}", message.type(), message.transactionUuid());

        // 1. 메시지 타입에 맞는 핸들러 조회 (없으면 BusinessException → NACK)
        BlockchainSyncHandler handler = registry.get(message.type());

        // 2. 핸들러 실행
        //    - 정상 완료 → ACK
        //    - retryable 예외 전파 → NACK → DLQ
        handler.handle(message);

        log.info("[listener] 처리 완료. type={}, uuid={}", message.type(), message.transactionUuid());
    }
}
