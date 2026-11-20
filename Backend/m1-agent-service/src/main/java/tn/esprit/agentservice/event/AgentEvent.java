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
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant timestamp
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
                toolCallCount, errorCode, null, null, null);
    }

    public AgentEvent withLegacyAliases() {
        return new AgentEvent(eventId, eventType, producer, occurredAt, agentId, agentVersionId,
                executionId, status, durationMs, promptTokens, completionTokens, totalTokens,
                toolCallCount, errorCode, eventType.name(), producer, occurredAt);
    }
}
