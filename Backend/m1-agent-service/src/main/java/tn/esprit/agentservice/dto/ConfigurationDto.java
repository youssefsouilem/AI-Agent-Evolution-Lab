package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tn.esprit.agentservice.service.TunableParameterCatalog;

import java.util.List;

public record ConfigurationDto(
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @Min(256) @Max(4096) Integer maxTokens,
        @Pattern(regexp = "concise|detaille") String style,
        @Min(1) @Max(10) Integer maxTurns,
        @Min(TunableParameterCatalog.TIMEOUT_SECONDS_MIN)
        @Max(TunableParameterCatalog.TIMEOUT_SECONDS_MAX) Integer timeoutSeconds,
        @NotNull List<@NotNull Long> allowedToolIds
) {
}
