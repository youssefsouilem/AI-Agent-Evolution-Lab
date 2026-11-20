package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ConfigurationDto(
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @Min(256) @Max(4096) Integer maxTokens,
        @Pattern(regexp = "concise|detaille") String style,
        @Min(1) @Max(10) Integer maxTurns,
        @Min(1) Integer timeoutSeconds,
        @NotNull List<@NotNull Long> allowedToolIds
) {
}
