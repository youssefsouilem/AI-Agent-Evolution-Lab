package tn.esprit.agentservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.esprit.agentservice.entity.OutboxEvent;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.util.UUID;

@Component
public class AgentEventPublisher {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean legacyAliases;
    private final String agentCreatedKey;
    private final String agentUpdatedKey;
    private final String agentDeletedKey;
    private final String agentExecutedKey;
    private final String executionFailedKey;

    public AgentEventPublisher(
            OutboxEventRepository repository,
            ObjectMapper objectMapper,
            @Value("${m1.events.legacy-aliases:true}") boolean legacyAliases,
            @Value("${app.events.routing-keys.agent-created:agent.created}") String agentCreatedKey,
            @Value("${app.events.routing-keys.agent-updated:agent.updated}") String agentUpdatedKey,
            @Value("${app.events.routing-keys.agent-deleted:agent.deleted}") String agentDeletedKey,
            @Value("${app.events.routing-keys.agent-executed:agent.executed}") String agentExecutedKey,
            @Value("${app.events.routing-keys.agent-execution-failed:agent.execution.failed}") String executionFailedKey
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.legacyAliases = legacyAliases;
        this.agentCreatedKey = agentCreatedKey;
        this.agentUpdatedKey = agentUpdatedKey;
        this.agentDeletedKey = agentDeletedKey;
        this.agentExecutedKey = agentExecutedKey;
        this.executionFailedKey = executionFailedKey;
    }

    public void publish(AgentEvent event) {
        OutboxEvent row = new OutboxEvent();
        row.setId(event.eventId());
        row.setEventType(event.eventType().name());
        row.setRoutingKey(routingKey(event.eventType()));
        try {
            row.setPayload(objectMapper.writeValueAsString(legacyAliases ? event.withLegacyAliases() : event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de sérialiser l'événement.", exception);
        }
        row.setStatus(OutboxStatus.PENDING);
        repository.save(row);
    }

    private String routingKey(AgentEventType eventType) {
        return switch (eventType) {
            case AGENT_CREATED -> agentCreatedKey;
            case AGENT_UPDATED -> agentUpdatedKey;
            case AGENT_DELETED -> agentDeletedKey;
            case AGENT_EXECUTED -> agentExecutedKey;
            case AGENT_EXECUTION_FAILED -> executionFailedKey;
        };
    }
}
