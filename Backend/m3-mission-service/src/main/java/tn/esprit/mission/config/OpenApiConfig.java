package tn.esprit.mission.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI missionApi() {
        return new OpenAPI().info(
            new Info()
                .title("AI Agent Evolution Lab - M3 Missions")
                .version("1.0.0")
                .description(
                    "Phase 1 : API de gestion des missions et workflows (sans M1/LangGraph)."
                )
        );
    }
}
