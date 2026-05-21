package family.fisa.hangangpay.client.bank.dto;

import java.time.LocalDateTime;

public record BlockchainLedgerResponse(
        Long id,
        Long institutionId,
        String txHash,
        String status,
        Long blockNumber,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt) {}
