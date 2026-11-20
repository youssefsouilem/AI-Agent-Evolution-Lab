package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record ExecuteRequest(
        @Positive Long versionId,
        @NotBlank String input,
        Map<String, Object> context
) {
}
