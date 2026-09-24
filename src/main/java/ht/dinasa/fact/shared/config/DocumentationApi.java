package ht.dinasa.fact.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentationApi {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("DINASA – API Facturation transporteurs")
                .version("v1")
                .description("Factures, relevés de compte et paramètres de facturation."));
    }
}