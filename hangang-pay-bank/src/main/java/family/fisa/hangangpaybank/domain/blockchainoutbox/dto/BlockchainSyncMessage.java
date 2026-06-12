package family.fisa.hangangpaybank.domain.blockchainoutbox.dto;

import com.fasterxml.jackson.databind.JsonNode;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;

/** Blockchain Outbox 이벤트를 MQ Broker로 발행하기 위한 메시지 DTO. 메시지 식별 정보와 블록체인 동기화에 필요한 payload를 포함한다. */
public record BlockchainSyncMessage(
        String messageId,
        Long outboxId,
        Long blockchainLedgerId,
        String transactionUuid,
        BlockchainSyncType type,
        String orderingKey, // ordering key로 routing하기 위해 필요
        JsonNode payload) {}
