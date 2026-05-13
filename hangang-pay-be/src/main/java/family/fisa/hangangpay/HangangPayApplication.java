package family.fisa.hangangpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class HangangPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HangangPayApplication.class, args);
    }
}
