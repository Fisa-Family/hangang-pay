package family.fisa.hangangpaybank.domain.blockchainoutbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncMessagePublisher;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainOutboxPublisherScheduler {
    private final BlockchainOutboxRepository blockchainOutboxRepository;
    private final BlockchainSyncMessagePublisher publisher;
    private final ObjectMapper objectMapper;

    /**
     * outbox에서 status=NEW 상태인 항목을 찾아 발행한다. 요청 스레드가 DB 트랜잭션 안에서 outbox(NEW)를 발행하고, 스케줄러가 이를 감지하여 MQ에
     * 전송한다. publish 실패 시 retry count가 증가되며, publish 실패가 MAX_RETRY에 도달하면 FAILED로 전환된다.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:5000ms}")
    @Transactional
    public void publishPending() {
        List<BlockchainOutbox> candidates =
                blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW);

        for (BlockchainOutbox outbox : candidates) {
            try {
                publisher.publish(buildMessage(outbox));
                outbox.markSent();
                log.info(
                        "[outbox] publish 성공. outboxId={}, transactionUuid={}",
                        outbox.getId(),
                        outbox.getTransactionUuid());
            } catch (Exception e) {
                log.warn(
                        "[outbox] publish 실패. outboxId={}, retryCount={}",
                        outbox.getId(),
                        outbox.getRetryCount());
                // retry 횟수를 늘리거나 FAILED로 전환
                outbox.incrementRetryOrFail();
            }
        }
    }

    /** outbox로부터 message를 발행한다. */
    private BlockchainSyncMessage buildMessage(BlockchainOutbox outbox) throws Exception {
        // Outbox payload에 저장되어있는 JSON 문자열을 JsonNode 객체로 파싱
        JsonNode payload = objectMapper.readTree(outbox.getPayload());

        return new BlockchainSyncMessage(
                BlockchainSyncMessage.messageIdFromOutboxId(outbox.getId()),
                outbox.getId(),
                outbox.getBlockchainLedgerId(),
                outbox.getTransactionUuid(),
                outbox.getType(),
                1,
                payload);
    }
}
