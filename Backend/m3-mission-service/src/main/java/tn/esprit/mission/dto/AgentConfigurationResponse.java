package tn.esprit.mission.dto;

import java.util.List;

public record AgentConfigurationResponse(
        Double temperature,
        Integer maxTurns,
        Integer timeoutSeconds,
        List<Long> allowedToolIds
) {
}