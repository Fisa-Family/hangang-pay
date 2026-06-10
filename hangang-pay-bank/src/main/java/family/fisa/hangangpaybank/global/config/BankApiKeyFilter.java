package family.fisa.hangangpaybank.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.global.code.error.GeneralErrorCode;
import family.fisa.hangangpaybank.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** hangang-pay-be에서 오는 요청인지 X-Bank-Api-Key 헤더로 검증한다. */
@Slf4j
@Component
public class BankApiKeyFilter extends OncePerRequestFilter {
    // API 키는 보안을 위해 해시 비교로 검증한다.
    private static final String API_KEY_HEADER = "X-Bank-Api-Key";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 실제 API 키는 application.properties 또는 환경 변수에서 주입받는다.
    @Value("${bank.api-key}")
    private String bankApiKey;

    // API 키는 해시로 저장되어 있어야 한다. (예: SHA-256)
    @Override
    protected void doFilterInternal( // 필터에서 API 키 검증 로직을 구현한다.
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isExcluded(request.getRequestURI())) { // 특정 URI는 API 키 검증에서 제외한다.
            filterChain.doFilter(request, response);
            return;
        }

        // 요청 헤더에서 API 키를 가져와서 서버에 저장된 API 키와 비교한다.
        String requestApiKey = request.getHeader(API_KEY_HEADER);
        if (requestApiKey == null || !isEqual(requestApiKey, bankApiKey)) {
            log.warn("Bank API Key 검증 실패: uri={}", request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        // API 키 검증이 성공하면 다음 필터로 요청을 전달한다.
        filterChain.doFilter(request, response);
    }

    // 특정 URI는 API 키 검증에서 제외한다. (예: 헬스체크, API 문서 등)
    private boolean isExcluded(String uri) {
        return uri.startsWith("/actuator")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/api-docs")
                || uri.startsWith("/v3/api-docs");
    }

    // API 키는 해시로 저장되어 있기 때문에, 요청에서 받은 API 키와 서버에 저장된 API 키를 비교한다.
    private boolean isEqual(String requestApiKey, String serverApiKey) {
        return MessageDigest.isEqual(
                requestApiKey.getBytes(StandardCharsets.UTF_8),
                serverApiKey.getBytes(StandardCharsets.UTF_8));
    }

    // API 키 검증 실패 시 401 Unauthorized 응답을 반환한다.
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(GeneralErrorCode.COMMON_UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        OBJECT_MAPPER.writeValueAsString(
                                ApiResponse.onFailure(GeneralErrorCode.COMMON_UNAUTHORIZED)));
    }
}
