package family.fisa.hangangpay.client.bank.config;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BankClientConfigTest {

    @Test
    @DisplayName("bankRestClient은 모든 요청에 X-Bank-Api-Key 헤더를 부착한다")
    void bankRestClient_attachesApiKeyHeaderToEveryRequest() {
        BankClientConfig config = new BankClientConfig();
        ReflectionTestUtils.setField(config, "bankBaseUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(config, "bankApiKey", "test-bank-api-key");

        RestClient.Builder builder = config.bankRestClient().mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("http://localhost:8081/api/v1/transactions/charge"))
                .andExpect(header("X-Bank-Api-Key", "test-bank-api-key"))
                .andRespond(withSuccess());

        restClient.post().uri("/api/v1/transactions/charge").retrieve().toBodilessEntity();

        server.verify();
    }
}
