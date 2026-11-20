package tn.esprit.agentservice.dto;

import tn.esprit.agentservice.entity.AgentStatus;

import java.time.LocalDateTime;

public record AgentResponse(
        Long id,
        String name,
        String description,
        String ownerId,
        AgentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
