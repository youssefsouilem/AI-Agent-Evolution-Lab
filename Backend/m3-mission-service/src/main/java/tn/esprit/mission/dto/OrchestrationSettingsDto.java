package tn.esprit.mission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrchestrationSettingsDto(
    @NotNull @Min(1) @Max(10) Integer maxRetries,
    @NotNull @Min(10) @Max(3600) Integer maxDuration,
    @NotNull @Min(1) @Max(600) Integer maxStepDuration,
    @NotNull @Min(1) @Max(100) Integer maxSteps,
    @NotNull @Min(1) @Max(10) Integer maxConcurrent,
    @NotNull Boolean allowConditions
) {}
