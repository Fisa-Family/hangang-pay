package family.fisa.hangangpay.domain.transaction.service.charge;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;

/**
 * UNKNOWN/PROCESSING 충전을 은행 재조회로 확정하는 reconcile 전담 서비스.
 *
 * <p>{@link family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService}와 같은
 * 배치 전용 패턴. 충전은 사용자 대면 수동 복구 엔드포인트가 없어 배치 진입점만 갖는다.
 */
public interface ChargeReconcileService {

    /** 배치용: 스케줄러가 고른 대상을 bank 재조회로 한 건 확정한다(락만). */
    void reconcile(Transaction tx);
}
