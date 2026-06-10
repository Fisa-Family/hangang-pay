package family.fisa.hangangpaybank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class BankApiKeyFilterTest {

    private static final String VALID_API_KEY = "test-bank-api-key";

    private BankApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BankApiKeyFilter();
        ReflectionTestUtils.setField(filter, "bankApiKey", VALID_API_KEY);
    }

    @Test
    @DisplayName("API Key 헤더가 없으면 401을 반환하고 필터 체인을 타지 않는다")
    void doFilter_whenHeaderMissing_returnsUnauthorized() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/transactions/charge");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("API Key 헤더 값이 일치하지 않으면 401을 반환한다")
    void doFilter_whenHeaderMismatch_returnsUnauthorized() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/transactions/charge");
        request.addHeader("X-Bank-Api-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("API Key 헤더 값이 일치하면 필터 체인을 통과한다")
    void doFilter_whenHeaderMatches_passesThroughChain() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/transactions/charge");
        request.addHeader("X-Bank-Api-Key", VALID_API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("actuator 경로는 API Key 헤더 없이도 통과한다")
    void doFilter_whenActuatorPath_passesThroughWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
