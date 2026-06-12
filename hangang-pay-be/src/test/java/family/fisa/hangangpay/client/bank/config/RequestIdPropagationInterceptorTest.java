package family.fisa.hangangpay.client.bank.config;

import static org.assertj.core.api.Assertions.assertThat;

import family.fisa.hangangpay.global.logging.MdcLoggingFilter;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

@DisplayName("RequestIdPropagationInterceptor - bank 호출에 requestId 전파")
class RequestIdPropagationInterceptorTest {

    private final RequestIdPropagationInterceptor interceptor =
            new RequestIdPropagationInterceptor();

    private final ClientHttpRequestExecution execution =
            (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC에 requestId가 있으면 X-Request-Id 헤더로 전파한다")
    void requestId_헤더_전파() throws IOException {
        MDC.put(MdcLoggingFilter.REQUEST_ID, "req-abc");
        MockClientHttpRequest request =
                new MockClientHttpRequest(HttpMethod.POST, URI.create("http://bank/api"));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(MdcLoggingFilter.REQUEST_ID_HEADER))
                .isEqualTo("req-abc");
    }

    @Test
    @DisplayName("MDC에 requestId가 없으면(스케줄러 등) 헤더를 붙이지 않는다")
    void requestId_없으면_헤더_미설정() throws IOException {
        MockClientHttpRequest request =
                new MockClientHttpRequest(HttpMethod.GET, URI.create("http://bank/api"));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(MdcLoggingFilter.REQUEST_ID_HEADER)).isNull();
    }
}
