package family.fisa.hangangpay.global.logging;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.PARTY_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

@DisplayName("MdcLoggingFilter - 요청 단위 로그 컨텍스트")
class MdcLoggingFilterTest {

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    /** 체인 실행 시점의 MDC 스냅샷을 캡처한다. */
    private static class CapturingFilterChain implements FilterChain {
        private final Map<String, String> capturedMdc = new HashMap<>();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            if (context != null) {
                capturedMdc.putAll(context);
            }
        }
    }

    @Test
    @DisplayName("로그인 세션이 있으면 requestId, partyId, role이 MDC에 설정된다")
    void 세션_있으면_MDC_설정() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PARTY_ID, 7L);
        session.setAttribute(ROLE, "USER");
        request.setSession(session);
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.capturedMdc.get(MdcLoggingFilter.REQUEST_ID)).isNotBlank();
        assertThat(chain.capturedMdc.get(PARTY_ID)).isEqualTo("7");
        assertThat(chain.capturedMdc.get(ROLE)).isEqualTo("USER");
    }

    @Test
    @DisplayName("세션이 없으면 requestId만 설정된다")
    void 세션_없으면_requestId만() throws ServletException, IOException {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.capturedMdc.get(MdcLoggingFilter.REQUEST_ID)).isNotBlank();
        assertThat(chain.capturedMdc).doesNotContainKeys(PARTY_ID, ROLE);
    }

    @Test
    @DisplayName("X-Request-Id 헤더가 있으면 그 값을 requestId로 쓰고 응답 헤더로 돌려준다")
    void 요청_헤더의_requestId_재사용() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcLoggingFilter.REQUEST_ID_HEADER, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.capturedMdc.get(MdcLoggingFilter.REQUEST_ID)).isEqualTo("req-123");
        assertThat(response.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("req-123");
    }

    @Test
    @DisplayName("체인 종료 후 MDC가 비워져 스레드 풀 재사용 시 오염이 없다")
    void 체인_종료_후_MDC_정리() throws ServletException, IOException {
        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new CapturingFilterChain());

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("체인에서 예외가 발생해도 MDC가 비워진다")
    void 예외_발생해도_MDC_정리() {
        FilterChain throwingChain =
                (request, response) -> {
                    throw new ServletException("boom");
                };

        assertThrows(
                ServletException.class,
                () ->
                        filter.doFilter(
                                new MockHttpServletRequest(),
                                new MockHttpServletResponse(),
                                throwingChain));

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
