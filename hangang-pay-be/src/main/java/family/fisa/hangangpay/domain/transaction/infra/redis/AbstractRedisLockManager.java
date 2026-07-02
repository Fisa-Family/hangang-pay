package family.fisa.hangangpay.domain.transaction.infra.redis;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 기반 단일 키 분산 락 공통 로직. SET NX로 선점하고, 내가 저장한 token일 때만 Lua로 원자적으로 해제한다. payment/cancel 락 매니저가
 * 공유한다.
 */
public abstract class AbstractRedisLockManager {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    // 다른 요청의 lock을 지우지 않도록 내가 저장한 token일 때만 삭제한다.
    // Redis 안에서 GET 비교와 DEL 삭제를 한 번에 실행해 중간 경합을 막는다.
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    protected AbstractRedisLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 락 키 prefix. 예: "payment:lock:" */
    protected abstract String keyPrefix();

    /** 락 선점 실패(이미 처리 중) 시 던질 에러코드 */
    protected abstract BaseErrorCode alreadyProcessingError();

    /**
     * lockId를 키로 락을 원자적으로 선점하고 supplier를 실행한다. 이미 선점돼 있으면 {@link #alreadyProcessingError()}를 던지고,
     * 완료 후에는 내가 저장한 token일 때만 해제한다.
     */
    protected final <T> T runWithLock(String lockId, Supplier<T> supplier) {
        String key = keyPrefix() + lockId;
        String token = UUID.randomUUID().toString();

        // setIfAbsent는 Redis SET NX 역할이다. 같은 키 실행은 하나만 lock을 선점할 수 있다.
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(alreadyProcessingError());
        }

        try {
            return supplier.get();
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        }
    }
}
