package tn.esprit.mission.dto;

public record AgentVersionResponse(
        Long id,
        Integer versionNumber,
        String model,
        String systemPrompt,
        Boolean active,
        AgentConfigurationResponse configuration
) {
}