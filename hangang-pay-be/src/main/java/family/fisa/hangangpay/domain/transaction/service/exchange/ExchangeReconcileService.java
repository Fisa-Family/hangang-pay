package family.fisa.hangangpay.domain.transaction.service.exchange;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;

/** PENDING/UNKNOWN으로 남은 EXCHANGE를 bank 재조회해 SUCCESS/FAILED로 확정하는 서비스. */
public interface ExchangeReconcileService {

    /** 한 건 reconcile: bank 조회 결과로 확정 */
    void reconcile(Transaction tx);
}
