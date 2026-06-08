package family.fisa.hangangpay.domain.transaction.internal.exchange;

/** 같은 transactionUuid인지 + 요청 내용이 변조되지 않았는지 비교하기 위한 해시 생성기 */
public interface ExchangeRequestHashGenerator {
    String generate(Long partyId, String uuid);
}
