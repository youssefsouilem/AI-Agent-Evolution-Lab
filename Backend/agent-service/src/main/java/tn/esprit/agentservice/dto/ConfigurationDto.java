package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConfigurationDto(
        @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
        @Min(1) int maxTurns,
        @Min(1) int timeoutSeconds,
        @NotNull List<@NotNull Long> allowedToolIds
) {
}
