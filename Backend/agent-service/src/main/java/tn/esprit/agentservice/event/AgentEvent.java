package tn.esprit.agentservice.event;

import java.time.Instant;
import java.util.UUID;

public record AgentEvent(
        UUID eventId,
        AgentEventType eventType,
        String producer,
        Long agentId,
        Long agentVersionId,
        Instant occurredAt
) {

    public static AgentEvent create(
            AgentEventType eventType,
            String producer,
            Long agentId,
            Long agentVersionId
    ) {
        return new AgentEvent(UUID.randomUUID(), eventType, producer, agentId, agentVersionId, Instant.now());
    }
}
