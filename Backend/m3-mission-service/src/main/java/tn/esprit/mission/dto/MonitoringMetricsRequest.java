package tn.esprit.mission.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// Provisional M3 -> M6 contract, to confirm against the real M6 API.
public record MonitoringMetricsRequest(
    @NotNull @Positive Long executionId,
    @NotNull @Min(0) Long durationMs,
    @NotNull @Min(0) Long tokensUsed,
    @NotNull @Min(0) Integer attempts,
    @NotNull @Pattern(regexp = "COMPLETED|FAILED") String status
) {}
