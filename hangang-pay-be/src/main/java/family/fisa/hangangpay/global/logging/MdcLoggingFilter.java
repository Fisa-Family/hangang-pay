package family.fisa.hangangpay.global.logging;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.PARTY_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.ROLE;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 단위 로그 컨텍스트(MDC)를 설정한다.
 *
 * <p>requestId는 업스트림(X-Request-Id 헤더)이 있으면 이어받고 없으면 생성한다. ECS 구조화 로깅에서는 MDC가 JSON 필드로 자동 포함되어
 * Loki에서 requestId 하나로 요청 전체 로그를 묶을 수 있다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String requestId = resolveRequestId(request);
            MDC.put(REQUEST_ID, requestId);
            putSessionAttributes(request);
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(headerValue)) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }

    private void putSessionAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        Object partyId = session.getAttribute(PARTY_ID);
        if (partyId != null) {
            MDC.put(PARTY_ID, String.valueOf(partyId));
        }
        Object role = session.getAttribute(ROLE);
        if (role != null) {
            MDC.put(ROLE, String.valueOf(role));
        }
    }
}
