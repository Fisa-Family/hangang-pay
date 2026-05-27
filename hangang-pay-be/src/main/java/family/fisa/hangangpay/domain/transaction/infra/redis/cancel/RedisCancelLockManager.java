package family.fisa.hangangpay.domain.transaction.infra.redis.cancel;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelLockManager;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedisCancelLockManager implements CancelLockManager {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "cancel:lock:";
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

    @Override
    public <T> T withCancelLock(String originalPaymentUuid, Supplier<T> supplier) {
        String key = KEY_PREFIX + originalPaymentUuid;
        String token = UUID.randomUUID().toString();

        // 1. SET NX 작업
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(TransactionErrorCode.CANCEL_ALREADY_PROCESSING);
        }

        try {
            // 2. 취소 처리 실행
            return supplier.get();
        } finally {
            // 3. 내가 저장한 token일 때만 삭제 — 다른 요청의 락 실수 해제 방지
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        }

    }
}
