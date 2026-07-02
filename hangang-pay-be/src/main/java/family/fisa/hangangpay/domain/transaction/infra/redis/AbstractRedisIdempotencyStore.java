package family.fisa.hangangpay.domain.transaction.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 기반 멱등성 스토어 공통 로직. charge/payment/exchange/cancel 어댑터가 공유하는 키 생성, 직렬화, 원자적 선점,
 * beginExecution 판정을 담는다.
 *
 * <p>플로우별 차이는 훅으로 노출한다: 키 prefix, record 타입, 에러코드, FAILED 상태 재요청 처리 여부.
 *
 * @param <S> 플로우별 실행 응답 snapshot 타입
 * @param <R> 플로우별 멱등성 레코드 타입
 */
public abstract class AbstractRedisIdempotencyStore<S, R extends IdempotencyRecord<S>> {

    protected static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    protected AbstractRedisIdempotencyStore(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 멱등성 Redis 키 prefix. 예: "charge:idempotency:" */
    protected abstract String keyPrefix();

    /** 역직렬화 대상 record 타입 */
    protected abstract Class<R> recordType();

    /** record 없음 에러코드 */
    protected abstract BaseErrorCode notFoundError();

    /** record 직렬화/역직렬화 실패 에러코드 */
    protected abstract BaseErrorCode invalidError();

    /**
     * FAILED 상태의 기존 record를 재요청 시 ALREADY_FAILED로 거절할지 여부. 기본 true. charge는 기존 동작상 FAILED여도 진행 중으로
     * 보므로 false로 재정의한다.
     */
    protected boolean honorFailedState() {
        return true;
    }

    /** prefix를 붙인 멱등성 Redis 키 반환 */
    protected final String key(String idempotencyId) {
        return keyPrefix() + idempotencyId;
    }

    /**
     * 첫 요청을 원자적으로 선점(SET NX)하고, 이미 존재하면 기존 record 상태로 판정한다.
     *
     * <p>created → NEW_REQUEST / requestHash 불일치 → CONFLICT / snapshot 있음 → RETURN_SNAPSHOT /
     * (honor 시) FAILED → ALREADY_FAILED / 그 외 → PROCESSING
     */
    protected final IdempotencyDecision<S> begin(IdempotencyKey idempotencyKey, R seedRecord) {

        String redisKey = key(idempotencyKey.idempotencyId());

        // 첫 실행 요청을 원자적으로 선점한다. 같은 키 동시 요청 시 하나만 created=true가 된다.
        Boolean created =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(redisKey, serialize(seedRecord), IDEMPOTENCY_TTL);

        if (Boolean.TRUE.equals(created)) {
            return IdempotencyDecision.newRequest();
        }

        R existing = readRecord(redisKey);

        // 같은 키 + 다른 requestHash → 충돌
        if (!Objects.equals(existing.requestHash(), idempotencyKey.requestHash())) {
            return IdempotencyDecision.conflict();
        }

        // 완료된 동일 요청 → snapshot 재사용
        S snapshot = existing.responseSnapshot();
        if (snapshot != null) {
            return IdempotencyDecision.returnSnapshot(snapshot);
        }

        // snapshot 없고 FAILED로 확정된 요청 → 재시도 거절
        if (honorFailedState() && existing.status() == TransactionStatus.FAILED) {
            return IdempotencyDecision.alreadyFailed();
        }

        // snapshot 없음 → 기존 요청이 아직 처리 중
        return IdempotencyDecision.processing();
    }

    /** Redis에서 레코드 역직렬화 후 반환 */
    protected final R readRecord(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new BusinessException(notFoundError());
        }
        try {
            return objectMapper.readValue(value, recordType());
        } catch (JsonProcessingException e) {
            throw new BusinessException(invalidError());
        }
    }

    /** 레코드를 TTL과 함께 Redis에 저장(덮어쓰기) */
    protected final void save(String key, R record) {
        redisTemplate.opsForValue().set(key, serialize(record), IDEMPOTENCY_TTL);
    }

    /** 레코드 JSON 직렬화 후 반환 */
    private String serialize(R record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new BusinessException(invalidError());
        }
    }
}
