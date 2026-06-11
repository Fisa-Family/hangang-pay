package family.fisa.hangangpaybank.global.logging;

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
    @DisplayName("BE가 보낸 X-Request-Id 헤더를 requestId로 이어받는다")
    void 업스트림_requestId_이어받기() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcLoggingFilter.REQUEST_ID_HEADER, "req-from-be");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.capturedMdc.get(MdcLoggingFilter.REQUEST_ID)).isEqualTo("req-from-be");
        assertThat(response.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("req-from-be");
    }

    @Test
    @DisplayName("헤더가 없으면(직접 호출 등) requestId를 새로 생성한다")
    void 헤더_없으면_생성() throws ServletException, IOException {
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.capturedMdc.get(MdcLoggingFilter.REQUEST_ID)).isNotBlank();
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
