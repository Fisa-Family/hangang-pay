package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.entity.LedgerStatus;
import lombok.Builder;

@Builder
public record ExchangeStatusResponse(
        String transactionUuid, Long bankTransactionId, String status) {

    public static ExchangeStatusResponse of(String transactionUuid, AccountLedger accountLedger) {
        return ExchangeStatusResponse.builder()
                .transactionUuid(transactionUuid)
                .bankTransactionId(accountLedger.getId())
                .status(mapStatus(accountLedger.getStatus()))
                .build();
    }

    private static String mapStatus(LedgerStatus status) {
        return switch (status) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> "FAILED";
            case PENDING -> "PROCESSING";
        };
    }
}
