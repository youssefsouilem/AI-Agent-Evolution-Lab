package tn.esprit.agentservice.dto;

import java.time.LocalDateTime;

public record VersionResponse(
        Long id,
        Long agentId,
        int versionNumber,
        String model,
        String systemPrompt,
        String changeLog,
        String source,
        Long studyId,
        String label,
        boolean active,
        LocalDateTime createdAt,
        ConfigurationDto configuration
) {
}
