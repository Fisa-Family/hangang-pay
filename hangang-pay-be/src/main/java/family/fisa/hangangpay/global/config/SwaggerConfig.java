package family.fisa.hangangpay.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI swagger() {
        SecurityScheme sessionAuth =
                new SecurityScheme().type(Type.APIKEY).in(In.COOKIE).name("JSESSIONID");

        Info info =
                new Info().title("Hangang Pay").description("CBDC 기반 소상공인 결제 서비스").version("0.0.1");

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes("sessionAuth", sessionAuth))
                .addSecurityItem(new SecurityRequirement().addList("sessionAuth"));
    }
}
