package family.fisa.hangangpay.client.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BankClientConfig {

    @Value("${bank.base-url}")
    private String bankBaseUrl;

    @Value("${bank.api-key}")
    private String bankApiKey;

    @Bean
    public RestClient bankRestClient() {
        // bank 서버와 통신할 RestClient 생성. 모든 요청에 인증용 API Key 헤더를 부착한다.
        return RestClient.builder()
                .baseUrl(bankBaseUrl)
                .defaultHeader("X-Bank-Api-Key", bankApiKey)
                .build();
    }
}
