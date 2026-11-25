package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentAssistRequest(
        @NotBlank @Size(max = 2000) String description,
        @Pattern(regexp = "fr") String language,
        @Size(max = 100) String provider,
        @Size(max = 100) String model
) { }
