package family.fisa.hangangpay.domain.transaction.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.transaction.infra.redis.payment.RedisPaymentIntentDedupStore;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisPaymentIntentDedupStoreTest {

    private static final Duration DEDUP_TTL = Duration.ofSeconds(30);
    private static final String FINGERPRINT = "intent-fingerprint";
    private static final String NEW_TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String EXISTING_TRANSACTION_UUID = "22222222-2222-2222-2222-222222222222";
    private static final String KEY = "payment:intent:dedup:" + FINGERPRINT;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisPaymentIntentDedupStore redisPaymentIntentDedupStore;

    @BeforeEach
    void setUp() {
        redisPaymentIntentDedupStore = new RedisPaymentIntentDedupStore(redisTemplate);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("첫 intent fingerprint 선점이면 empty를 반환하고 Redis get을 호출하지 않는다")
    void reserve_firstFingerprintReturnsEmpty() {
        given(valueOperations.setIfAbsent(eq(KEY), eq(NEW_TRANSACTION_UUID), eq(DEDUP_TTL)))
                .willReturn(true);

        Optional<String> result =
                redisPaymentIntentDedupStore.reserve(FINGERPRINT, NEW_TRANSACTION_UUID);

        assertThat(result).isEmpty();
        verify(valueOperations).setIfAbsent(KEY, NEW_TRANSACTION_UUID, DEDUP_TTL);
        verify(valueOperations, never()).get(KEY);
    }

    @Test
    @DisplayName("이미 선점된 intent fingerprint이면 Redis에 저장된 기존 UUID를 반환한다")
    void reserve_existingFingerprintReturnsStoredTransactionUuid() {
        given(valueOperations.setIfAbsent(eq(KEY), eq(NEW_TRANSACTION_UUID), eq(DEDUP_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(EXISTING_TRANSACTION_UUID);

        Optional<String> result =
                redisPaymentIntentDedupStore.reserve(FINGERPRINT, NEW_TRANSACTION_UUID);

        assertThat(result).contains(EXISTING_TRANSACTION_UUID);
        verify(valueOperations).get(KEY);
    }
}
