package family.fisa.hangangpaybank.domain.blockchainoutbox.dto;

import com.fasterxml.jackson.databind.JsonNode;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;

public record BlockchainSyncMessage(
        String messageId,
        Long outboxId,
        Long blockchainLedgerId,
        String transactionUuid,
        BlockchainSyncType type,
        int payloadVersion,
        JsonNode payload) {

    public static String messageIdFromOutboxId(Long outboxId) {
        return "outbox-" + outboxId;
    }
}
