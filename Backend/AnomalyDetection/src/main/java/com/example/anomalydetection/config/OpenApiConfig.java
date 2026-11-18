package com.example.anomalydetection.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI anomalyDetectionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Anomaly Detection API")
                        .description("API for anomaly checks, alerts, and model version management.")
                        .version("v1.0")
                        .contact(new Contact().name("AI Agent Evolution Lab")))
                .externalDocs(new ExternalDocumentation()
                        .description("AI Agent Evolution Lab documentation"));
    }
}
