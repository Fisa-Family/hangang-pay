package family.fisa.hangangpay.client.bank.config;

import family.fisa.hangangpay.global.logging.MdcLoggingFilter;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * MDC의 requestId를 bank 호출 헤더로 전파해 BE-bank 로그를 같은 ID로 묶는다.
 *
 * <p>스케줄러처럼 HTTP 요청 밖에서 호출되면 MDC가 비어 있으므로 헤더를 붙이지 않는다.
 */
public class RequestIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String requestId = MDC.get(MdcLoggingFilter.REQUEST_ID);
        if (requestId != null) {
            request.getHeaders().set(MdcLoggingFilter.REQUEST_ID_HEADER, requestId);
        }
        return execution.execute(request, body);
    }
}
