package family.fisa.hangangpay.domain.transaction.infra.redis;

import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;

/**
 * Redis 멱등성 레코드 공통 계약. {@link AbstractRedisIdempotencyStore}가 판정에 필요한 최소 접근자만 노출한다. 플로우별 record가
 * 구현하며, 각 record는 저장/직렬화 형태(필드·팩토리)를 자유롭게 유지한다.
 *
 * @param <S> 플로우별 실행 응답 snapshot 타입
 */
public interface IdempotencyRecord<S> {

    /** 요청 해시 (충돌 감지용 SHA-256) */
    String requestHash();

    /** 현재 거래 상태 */
    TransactionStatus status();

    /** 멱등 재사용 응답 (완료 전 null) */
    S responseSnapshot();
}
