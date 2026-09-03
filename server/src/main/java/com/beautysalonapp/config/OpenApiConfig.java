package com.beautysalonapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI beautySalonAppOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BeautySalonApp API")
                .version("v1")
                .description("Güzellik merkezi yönetim yazılımı - yerel REST API")
                .license(new License().name("Ticari / kapalı kaynak")));
    }
}
