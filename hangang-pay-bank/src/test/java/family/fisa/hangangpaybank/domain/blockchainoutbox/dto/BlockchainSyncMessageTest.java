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
    @DisplayName("BlockchainSyncMessage는 Jackson 직렬화/역직렬화가 가능하다")
    void serializesAndDeserializesWithJackson() throws Exception {
        JsonNode payload =
                objectMapper.valueToTree(
                        new PaymentBlockchainPayload("0xABCD", "0x1234", new BigDecimal("100.00")));

        BlockchainSyncMessage message =
                new BlockchainSyncMessage(
                        "outbox-7", 7L, 3L, "uuid-1111", BlockchainSyncType.PAYMENT, payload);

        String json = objectMapper.writeValueAsString(message);
        BlockchainSyncMessage deserialized =
                objectMapper.readValue(json, BlockchainSyncMessage.class);

        assertThat(deserialized.messageId()).isEqualTo(message.messageId());
        assertThat(deserialized.outboxId()).isEqualTo(7L);
        assertThat(deserialized.blockchainLedgerId()).isEqualTo(3L);
        assertThat(deserialized.transactionUuid()).isEqualTo("uuid-1111");
        assertThat(deserialized.type()).isEqualTo(BlockchainSyncType.PAYMENT);
        assertThat(deserialized.retryCount()).isZero();
    }

    @Test
    @DisplayName("retryCount가 없는 기존 메시지도 retryCount=0으로 역직렬화된다")
    void deserializesLegacyMessageWithoutRetryCount() throws Exception {
        String json =
                """
                {
                  "messageId": "outbox-7",
                  "outboxId": 7,
                  "blockchainLedgerId": 3,
                  "transactionUuid": "uuid-1111",
                  "type": "PAYMENT",
                  "payload": {"fromWalletAddress":"0xA","toWalletAddress":"0xB","amount":100}
                }
                """;

        BlockchainSyncMessage deserialized =
                objectMapper.readValue(json, BlockchainSyncMessage.class);

        assertThat(deserialized.retryCount()).isZero();
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
                new ExchangeBlockchainPayload(1L, "0xD", new BigDecimal("20"));

        assertThat(payment.fromWalletAddress()).isEqualTo("0xA");
        assertThat(cancel.originalTransactionUuid()).isEqualTo("orig-uuid");
        assertThat(charge.walletAddress()).isEqualTo("0xC");
        assertThat(exchange.walletAddress()).isEqualTo("0xD");
    }
}
