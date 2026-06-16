package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 rate limit 설정. application.yaml의 {@code payment.rate-limit} 키에 바인딩된다.
 *
 * <p>{@code enabled=false}면 모든 한도 검사를 건너뛴다(부하 테스트용). refill 값은 분/초 단위로 받아 limiter 내부에서 ms당
 * 비율로 환산한다.
 */
@ConfigurationProperties("payment.rate-limit")
public record PaymentRateLimitProperties(
        boolean enabled,
        Intent intent,
        Execute execute,
        Recovery recovery,
        BankOutbound bankOutbound) {

    // 1. 결제 의도 생성 — 사용자별 토큰 버킷
    public record Intent(long capacity, double refillPerMinute) {}

    // 2. 결제 실행 — 사용자별 슬라이딩 윈도우
    public record Execute(long windowMinutes, long limit) {}

    // 3. 결제 복구 — 사용자별 슬라이딩 윈도우
    public record Recovery(long windowSeconds, long limit) {}

    // 4. 은행 아웃바운드 — 전역 토큰 버킷
    public record BankOutbound(long capacity, double refillPerSecond) {}
}
