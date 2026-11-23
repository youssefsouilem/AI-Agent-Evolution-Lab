package tn.esprit.agentservice.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public record AgentEvent(
        UUID eventId,
        AgentEventType eventType,
        String producer,
        Instant occurredAt,
        Long agentId,
        Long agentVersionId,
        Long executionId,
        String status,
        Long durationMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer toolCallCount,
        String errorCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String event,
        @JsonInclude(JsonInclude.Include.NON_NULL) String source,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) String actorId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String actorRole,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason
) {
    public static AgentEvent create(
            AgentEventType type,
            String producer,
            Long agentId,
            Long versionId
    ) {
        return execution(type, producer, agentId, versionId, null, null, null,
                null, null, null, null, null);
    }

    public static AgentEvent create(
            AgentEventType type,
            String producer,
            Long agentId,
            Long versionId,
            String actorId,
            String actorRole,
            String reason
    ) {
        AgentEvent event = create(type, producer, agentId, versionId);
        return new AgentEvent(event.eventId, event.eventType, event.producer, event.occurredAt, event.agentId,
                event.agentVersionId, event.executionId, event.status, event.durationMs, event.promptTokens,
                event.completionTokens, event.totalTokens, event.toolCallCount, event.errorCode, event.event,
                event.source, event.timestamp, actorId, actorRole, reason);
    }

    public static AgentEvent execution(
            AgentEventType type,
            String producer,
            Long agentId,
            Long versionId,
            Long executionId,
            String status,
            Long durationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer toolCallCount,
            String errorCode
    ) {
        return new AgentEvent(UUID.randomUUID(), type, producer, Instant.now(), agentId, versionId,
                executionId, status, durationMs, promptTokens, completionTokens, totalTokens,
                toolCallCount, errorCode, null, null, null, null, null, null);
    }

    public AgentEvent withLegacyAliases() {
        return new AgentEvent(eventId, eventType, producer, occurredAt, agentId, agentVersionId,
                executionId, status, durationMs, promptTokens, completionTokens, totalTokens,
                toolCallCount, errorCode, eventType.name(), producer, occurredAt, actorId, actorRole, reason);
    }

    public AgentEvent withActor(String actorId, String actorRole) {
        return new AgentEvent(eventId, eventType, producer, occurredAt, agentId, agentVersionId, executionId,
                status, durationMs, promptTokens, completionTokens, totalTokens, toolCallCount, errorCode,
                event, source, timestamp, actorId, actorRole, reason);
    }
}
