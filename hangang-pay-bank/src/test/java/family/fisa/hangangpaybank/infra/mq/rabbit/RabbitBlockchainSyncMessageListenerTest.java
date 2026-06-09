package family.fisa.hangangpaybank.infra.mq.rabbit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncHandler;
import family.fisa.hangangpaybank.domain.blockchainoutbox.service.BlockchainSyncHandlerRegistry;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RabbitBlockchainSyncMessageListenerTest {

    @Mock private BlockchainSyncHandlerRegistry registry;
    @Mock private RabbitBlockchainRetryPublisher retryPublisher;
    @Mock private BlockchainSyncHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("retryable BusinessException은 retry queue로 재발행하고 원본 ACK를 위해 정상 반환한다")
    void retryableBusinessExceptionPublishesRetryAndReturns() {
        BlockchainSyncMessage message = message();
        RabbitBlockchainSyncMessageListener listener =
                new RabbitBlockchainSyncMessageListener(registry, retryPublisher);
        given(registry.get(BlockchainSyncType.PAYMENT)).willReturn(handler);
        givenHandlerThrows(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);

        listener.onMessage(message);

        verify(retryPublisher).publishRetryOrDlq(message);
    }

    @Test
    @DisplayName("non-retryable BusinessException은 전파해 최종 DLQ 안전망으로 보낸다")
    void nonRetryableBusinessExceptionRethrows() {
        BlockchainSyncMessage message = message();
        RabbitBlockchainSyncMessageListener listener =
                new RabbitBlockchainSyncMessageListener(registry, retryPublisher);
        given(registry.get(BlockchainSyncType.PAYMENT)).willReturn(handler);
        givenHandlerThrows(BlockchainErrorCode.BLOCKCHAIN_SYNC_HANDLER_NOT_FOUND);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_SYNC_HANDLER_NOT_FOUND);

        verify(retryPublisher, never()).publishRetryOrDlq(message);
    }

    private void givenHandlerThrows(BlockchainErrorCode code) {
        org.mockito.BDDMockito.willThrow(new BusinessException(code)).given(handler).handle(any());
    }

    private BlockchainSyncMessage message() {
        return new BlockchainSyncMessage(
                "outbox-1",
                1L,
                10L,
                "uuid-1",
                BlockchainSyncType.PAYMENT,
                objectMapper.createObjectNode());
    }
}
