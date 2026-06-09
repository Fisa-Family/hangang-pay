package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** PAYMENT 타입 블록체인 동기화 메시지 핸들러. */
@Component
@RequiredArgsConstructor
public class PaymentBlockchainSyncHandler implements BlockchainSyncHandler {

    private final BlockchainSyncProcessor processor;

    @Override
    public BlockchainSyncType type() {
        return BlockchainSyncType.PAYMENT;
    }

    @Override
    public void handle(BlockchainSyncMessage message) {
        processor.processPayment(message);
    }
}
