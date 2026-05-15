package family.fisa.hangangpay.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.cors(c -> c.configurationSource(corsConfigurationSource));

        // 계좌 도메인만 CSRF 예외 적용, 다른 도메인 POST 개발 시 경로 추가 필요
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/accounts/**"));

        // 경로별 접근 권한 설정, 새 도메인 개발 시 해당 경로 추가 필요
        http.authorizeHttpRequests(
                auth ->
                        auth
                                // Swagger UI 및 API 문서
                                .requestMatchers(
                                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                                .permitAll()
                                // 계좌 도메인만 임시 인증 비활성화 적용
                                .requestMatchers("/api/v1/accounts/**")
                                .permitAll()
                                .anyRequest()
                                .authenticated());

        return http.build();
    }
}
