package tn.esprit.agentservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VersionRequest(
        @NotBlank String model,
        @NotBlank String systemPrompt,
        String changeLog,
        @NotNull @Valid ConfigurationDto configuration
) {
}
