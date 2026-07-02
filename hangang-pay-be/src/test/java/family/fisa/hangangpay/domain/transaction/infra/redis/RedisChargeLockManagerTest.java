package family.fisa.hangangpay.domain.transaction.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.infra.redis.charge.RedisChargeLockManager;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisChargeLockManagerTest {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String KEY = "charge:lock:" + TRANSACTION_UUID;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisChargeLockManager redisChargeLockManager;

    @BeforeEach
    void setUp() {
        redisChargeLockManager = new RedisChargeLockManager(redisTemplate);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("lock 획득에 성공하면 supplier를 실행하고 lock을 해제한다")
    void withChargeLock_acquiredRunsSupplierAndReleasesLock() {
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(LOCK_TTL))).willReturn(true);

        String result = redisChargeLockManager.withChargeLock(TRANSACTION_UUID, () -> "success");

        assertThat(result).isEqualTo("success");
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), anyString());
    }

    @Test
    @DisplayName("lock 획득에 실패하면 CHARGE_ALREADY_PROCESSING 예외가 발생한다")
    void withChargeLock_lockBusyThrowsAlreadyProcessing() {
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(LOCK_TTL))).willReturn(false);

        assertThatThrownBy(
                        () ->
                                redisChargeLockManager.withChargeLock(
                                        TRANSACTION_UUID, () -> "should-not-run"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.CHARGE_ALREADY_PROCESSING);

        verify(redisTemplate, never())
                .execute(any(RedisScript.class), any(List.class), anyString());
    }

    @Test
    @DisplayName("supplier에서 예외가 발생해도 lock은 해제한다")
    void withChargeLock_supplierThrowsStillReleasesLock() {
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(LOCK_TTL))).willReturn(true);

        assertThatThrownBy(
                        () ->
                                redisChargeLockManager.withChargeLock(
                                        TRANSACTION_UUID,
                                        () -> {
                                            throw new IllegalArgumentException("fail");
                                        }))
                .isInstanceOf(IllegalArgumentException.class);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), anyString());
    }
}
