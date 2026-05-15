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

        // REST API 서버이므로 CSRF 비활성화 - 브라우저 폼 기반 공격 대상이 아님
        http.csrf(csrf -> csrf.disable());

        return http.build();
    }
}
