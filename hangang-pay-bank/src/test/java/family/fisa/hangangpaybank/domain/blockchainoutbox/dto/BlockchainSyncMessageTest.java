package family.fisa.hangangpaybank.domain.blockchainoutbox.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.CancelBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.ChargeBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.ExchangeBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.PaymentBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockchainSyncMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("messageIdFromOutboxId는 동일한 outboxId로 항상 같은 값을 반환한다")
    void messageIdIsStableForSameOutboxId() {
        String first = BlockchainSyncMessage.messageIdFromOutboxId(42L);
        String second = BlockchainSyncMessage.messageIdFromOutboxId(42L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("다른 outboxId는 다른 messageId를 반환한다")
    void differentOutboxIdProducesDifferentMessageId() {
        String a = BlockchainSyncMessage.messageIdFromOutboxId(1L);
        String b = BlockchainSyncMessage.messageIdFromOutboxId(2L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("BlockchainSyncMessage는 Jackson 직렬화/역직렬화가 가능하다")
    void serializesAndDeserializesWithJackson() throws Exception {
        JsonNode payload =
                objectMapper.valueToTree(
                        new PaymentBlockchainPayload("0xABCD", "0x1234", new BigDecimal("100.00")));

        BlockchainSyncMessage message =
                new BlockchainSyncMessage(
                        BlockchainSyncMessage.messageIdFromOutboxId(7L),
                        7L,
                        3L,
                        "uuid-1111",
                        BlockchainSyncType.PAYMENT,
                        1,
                        payload);

        String json = objectMapper.writeValueAsString(message);
        BlockchainSyncMessage deserialized =
                objectMapper.readValue(json, BlockchainSyncMessage.class);

        assertThat(deserialized.messageId()).isEqualTo(message.messageId());
        assertThat(deserialized.outboxId()).isEqualTo(7L);
        assertThat(deserialized.blockchainLedgerId()).isEqualTo(3L);
        assertThat(deserialized.transactionUuid()).isEqualTo("uuid-1111");
        assertThat(deserialized.type()).isEqualTo(BlockchainSyncType.PAYMENT);
        assertThat(deserialized.payloadVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("payload DTO들은 RabbitMQ/Kafka import 없이 컴파일된다")
    void payloadDtosHaveNoMqDependency() {
        PaymentBlockchainPayload payment =
                new PaymentBlockchainPayload("0xA", "0xB", new BigDecimal("50"));
        CancelBlockchainPayload cancel =
                new CancelBlockchainPayload("orig-uuid", "0xA", "0xB", new BigDecimal("50"));
        ChargeBlockchainPayload charge = new ChargeBlockchainPayload("0xC", new BigDecimal("10"));
        ExchangeBlockchainPayload exchange =
                new ExchangeBlockchainPayload("0xD", new BigDecimal("20"));

        assertThat(payment.fromWalletAddress()).isEqualTo("0xA");
        assertThat(cancel.originalTransactionUuid()).isEqualTo("orig-uuid");
        assertThat(charge.walletAddress()).isEqualTo("0xC");
        assertThat(exchange.walletAddress()).isEqualTo("0xD");
    }
}
