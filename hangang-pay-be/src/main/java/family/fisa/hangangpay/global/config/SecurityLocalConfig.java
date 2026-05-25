package family.fisa.hangangpay.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** local 프로필 전용 보안 설정, 지갑 잔액 조회를 인증 없이 허용 (@Order(1)로 주 체인보다 먼저 매칭) */
@Configuration
@Profile("local")
public class SecurityLocalConfig {

    /** 지갑 잔액 조회 전용 필터 체인, local 프로필에서만 등록 */
    @Bean
    @Order(1)
    public SecurityFilterChain localWalletFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/wallet/balance")
                .csrf(csrf -> csrf.disable())
                // local Swagger 테스트용 지갑 잔액 조회만 인증 없이 허용
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
