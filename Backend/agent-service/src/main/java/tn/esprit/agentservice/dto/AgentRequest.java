package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.NotBlank;
import tn.esprit.agentservice.entity.AgentStatus;

public record AgentRequest(
        @NotBlank String name,
        String description,
        AgentStatus status
) {
}
