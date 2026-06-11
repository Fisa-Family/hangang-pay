package family.fisa.hangangpaybank.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * <p>BE가 X-Request-Id 헤더로 보낸 requestId를 이어받아 BE-bank 로그를 같은 ID로 묶는다. 헤더가 없으면(스케줄러, 직접 호출 등) 새로
 * 생성한다.
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
}
