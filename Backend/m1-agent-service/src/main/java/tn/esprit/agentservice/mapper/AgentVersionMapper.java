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
                version.getSource() == null ? "USER" : version.getSource(),
                version.getStudyId(),
                "Version " + version.getVersionNumber() + (version.isActive() ? " (active)" : ""),
                version.isActive(),
                version.getCreatedAt(),
                toDto(version.getConfiguration())
        );
    }

    public AgentConfiguration toEntity(ConfigurationDto configuration) {
        return AgentConfiguration.builder()
                .temperature(configuration.temperature() == null ? 0.7 : configuration.temperature())
                .maxTokens(configuration.maxTokens())
                .style(configuration.style())
                .maxTurns(configuration.maxTurns() == null ? 3 : configuration.maxTurns())
                .timeoutSeconds(configuration.timeoutSeconds() == null ? 30 : configuration.timeoutSeconds())
                .allowedToolIds(new ArrayList<>(configuration.allowedToolIds()))
                .build();
    }

    public AgentConfiguration copyConfiguration(AgentConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        return AgentConfiguration.builder()
                .temperature(configuration.getTemperature())
                .maxTokens(configuration.getMaxTokens())
                .style(configuration.getStyle())
                .maxTurns(configuration.getMaxTurns())
                .timeoutSeconds(configuration.getTimeoutSeconds())
                .allowedToolIds(configuration.getAllowedToolIds() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(configuration.getAllowedToolIds()))
                .build();
    }

    public ConfigurationDto toDto(AgentConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        return new ConfigurationDto(
                configuration.getTemperature(),
                configuration.getMaxTokens(),
                configuration.getStyle(),
                configuration.getMaxTurns(),
                configuration.getTimeoutSeconds(),
                new ArrayList<>(configuration.getAllowedToolIds())
        );
    }
}
