package family.fisa.hangangpay.global.init;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.MERCHANT_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.PARTY_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.ROLE;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.USER_ID;

import family.fisa.hangangpay.domain.merchant.repository.jpa.MerchantJpaRepository;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

// 로컬 전용 자동 로그인 필터 — FE의 VITE_AUTH_MOCK_ROLE로 역할 전환 (로그인 엔드포인트 URL 감지)
@Component
@Profile("local")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LocalAutoLoginFilter extends OncePerRequestFilter {

    // 테스트 소비자 조회용 리포지토리
    private final UserRepository userRepository;

    // 테스트 가맹점 조회용 리포지토리
    private final MerchantJpaRepository merchantJpaRepository;

    // 로그인 단락 응답에 CORS 헤더 추가용 설정 소스
    private final CorsConfigurationSource corsConfigurationSource;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        boolean isLoginEndpoint =
                HttpMethod.POST.matches(request.getMethod())
                        && uri.contains("/auth/")
                        && uri.endsWith("/login");

        if (isLoginEndpoint) {
            // 로그인 엔드포인트 — 세션 초기화 후 역할 재주입 (env 전환 시 세션 갱신 목적)
            HttpSession old = request.getSession(false);
            if (old != null) old.invalidate();
            if (isMerchantRequest(uri)) {
                autoLoginAsMerchant(request);
            } else {
                autoLoginAsUser(request);
            }
            applyCorsHeaders(request, response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // 일반 요청 — 세션 없을 때만 자동 주입 (레이스 컨디션 방지)
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(PARTY_ID) == null) {
            if (isMerchantRequest(uri)) {
                autoLoginAsMerchant(request);
            } else {
                autoLoginAsUser(request);
            }
        }

        filterChain.doFilter(request, response);
    }

    // 가맹점 전용 경로 여부 — /auth/merchants/ 또는 /merchant/ 포함
    private boolean isMerchantRequest(String uri) {
        return uri.contains("/merchants/") || uri.contains("/merchant/");
    }

    // CORS 필터보다 앞서 실행되므로 단락 응답에 직접 헤더 주입
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null) return;
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        if (config == null) return;
        if (config.checkOrigin(origin) == null) return;
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Vary", "Origin");
    }

    private void autoLoginAsUser(HttpServletRequest request) {
        userRepository
                .findByPhoneNumberWithParty("01012345678")
                .ifPresent(
                        user -> {
                            HttpSession s = request.getSession(true);
                            s.setAttribute(PARTY_ID, user.getParty().getId());
                            s.setAttribute(USER_ID, user.getId());
                            s.setAttribute(ROLE, "USER");
                        });
    }

    private void autoLoginAsMerchant(HttpServletRequest request) {
        merchantJpaRepository
                .findByPhoneNumberWithParty("0226001234")
                .ifPresent(
                        merchant -> {
                            HttpSession s = request.getSession(true);
                            s.setAttribute(PARTY_ID, merchant.getParty().getId());
                            s.setAttribute(MERCHANT_ID, merchant.getId());
                            s.setAttribute(ROLE, "MERCHANT");
                        });
    }
}
