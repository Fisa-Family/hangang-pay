package family.fisa.hangangpay;

import family.fisa.hangangpay.domain.transaction.infra.redis.payment.PaymentRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PaymentRateLimitProperties.class)
public class HangangPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HangangPayApplication.class, args);
    }
}
