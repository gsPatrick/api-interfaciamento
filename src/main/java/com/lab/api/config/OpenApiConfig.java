package com.lab.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Integração Laboratorial")
                        .version("1.0.0")
                        .description("Middleware para comunicação entre LIS e equipamentos de análises clínicas. " +
                                "Esta documentação interativa permite testar os endpoints da API.")
                );
    }
}