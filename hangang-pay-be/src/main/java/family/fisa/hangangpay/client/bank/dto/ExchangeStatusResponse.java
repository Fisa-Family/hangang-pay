package family.fisa.hangangpay.client.bank.dto;

public record ExchangeStatusResponse(
    String transactionUuid,
    Long bankTransactionId,
    String txHash
) {

}
