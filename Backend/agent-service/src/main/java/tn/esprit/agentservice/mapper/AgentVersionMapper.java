package tn.esprit.agentservice.mapper;

import org.springframework.stereotype.Component;
import tn.esprit.agentservice.dto.ConfigurationDto;
import tn.esprit.agentservice.dto.VersionResponse;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentVersion;

import java.util.ArrayList;

@Component
public class AgentVersionMapper {

    public VersionResponse toResponse(AgentVersion version) {
        return new VersionResponse(
                version.getId(),
                version.getAgent().getId(),
                version.getVersionNumber(),
                version.getModel(),
                version.getSystemPrompt(),
                version.getChangeLog(),
                version.isActive(),
                version.getCreatedAt(),
                toDto(version.getConfiguration())
        );
    }

    public AgentConfiguration toEntity(ConfigurationDto configuration) {
        return AgentConfiguration.builder()
                .temperature(configuration.temperature())
                .maxTurns(configuration.maxTurns())
                .timeoutSeconds(configuration.timeoutSeconds())
                .allowedToolIds(new ArrayList<>(configuration.allowedToolIds()))
                .build();
    }

    public ConfigurationDto toDto(AgentConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        return new ConfigurationDto(
                configuration.getTemperature(),
                configuration.getMaxTurns(),
                configuration.getTimeoutSeconds(),
                new ArrayList<>(configuration.getAllowedToolIds())
        );
    }
}
