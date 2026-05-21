package family.fisa.hangangpay.client.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BankClientConfig {

    @Value("${bank.base-url}")
    private String bankBaseUrl;

    @Bean
    public RestClient bankRestClient() {
        // 1. bank 서버와 통신할 RestClient 생성
        return RestClient.builder().baseUrl(bankBaseUrl).build();
    }
}
