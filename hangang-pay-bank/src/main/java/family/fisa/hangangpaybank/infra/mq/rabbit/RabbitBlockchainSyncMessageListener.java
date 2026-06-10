package family.fisa.hangangpaybank.infra.mq.rabbit;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncHandler;
import family.fisa.hangangpaybank.domain.blockchainoutbox.service.BlockchainSyncHandlerRegistry;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * blockchain-sync 큐를 구독하는 RabbitMQ 컨슈머.
 *
 * <p>단일 signer 안전성을 위해 concurrency = "1"로 고정한다. 동시에 여러 트랜잭션을 submit하면 nonce 충돌이 발생할 수 있다.
 *
 * <p>ACK/NACK 정책: - 핸들러가 정상 반환하면 Spring AMQP가 자동 ACK. - retryable BusinessException은 retry queue로
 * republish 후 ACK. - 그 외 예외는 전파해 NACK → 최종 DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitBlockchainSyncMessageListener {

    private static final Set<BlockchainErrorCode> RETRYABLE =
            Set.of(
                    BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED,
                    BlockchainErrorCode.BLOCKCHAIN_RECEIPT_TIMEOUT);

    private final BlockchainSyncHandlerRegistry registry;
    private final RabbitBlockchainRetryPublisher retryPublisher;

    @RabbitListener(queues = RabbitMqConfig.QUEUE, concurrency = "1")
    public void onMessage(BlockchainSyncMessage message) {
        log.info("[listener] 메시지 수신. type={}, uuid={}", message.type(), message.transactionUuid());

        try {
            // 1. 메시지 타입에 맞는 핸들러 조회 (없으면 BusinessException → NACK)
            BlockchainSyncHandler handler = registry.get(message.type());

            // 2. 핸들러 실행
            //    - 정상 완료 → ACK
            //    - retryable 예외 전파 → retry queue republish 후 ACK
            handler.handle(message);
        } catch (BusinessException e) {
            if (isRetryable(e)) {
                retryPublisher.publishRetryOrDlq(message);
                return;
            }
            throw e;
        }

        log.info("[listener] 처리 완료. type={}, uuid={}", message.type(), message.transactionUuid());
    }

    private boolean isRetryable(BusinessException exception) {
        return exception.getCode() instanceof BlockchainErrorCode code && RETRYABLE.contains(code);
    }
}
