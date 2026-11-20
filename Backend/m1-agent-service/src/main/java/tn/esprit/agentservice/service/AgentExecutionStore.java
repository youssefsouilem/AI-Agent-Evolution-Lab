package tn.esprit.agentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.event.AgentEvent;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.event.AgentEventType;
import tn.esprit.agentservice.repository.AgentExecutionRepository;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AgentExecutionStore {
    private final AgentExecutionRepository repository;
    private final ObjectMapper objectMapper;
    private final AgentEventPublisher eventPublisher;

    public AgentExecutionStore(
            AgentExecutionRepository repository,
            ObjectMapper objectMapper,
            AgentEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentExecution start(
            Long agentId,
            Long versionId,
            int versionNumber,
            String requestedBy,
            String input,
            String context,
            Map<String, Object> overrides,
            Map<String, Object> settings,
            String model
    ) {
        AgentExecution execution = new AgentExecution();
        execution.setAgentId(agentId);
        execution.setVersionId(versionId);
        execution.setVersionNumber(versionNumber);
        execution.setRequestedBy(requestedBy);
        execution.setInput(input);
        execution.setContext(context);
        execution.setOverrides(write(overrides));
        execution.setAppliedSettings(write(settings));
        execution.setModelUsed(model);
        execution.setStatus(ExecutionStatus.RUNNING);
        return repository.saveAndFlush(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(
            Long id,
            ExecutionStatus status,
            String errorCode,
            String errorMessage,
            String output,
            long durationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            int toolCallCount,
            String toolCalls
    ) {
        AgentExecution execution = repository.findById(id).orElseThrow();
        execution.setStatus(status);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(ErrorMessageSanitizer.sanitize(errorMessage));
        String safeOutput = ErrorMessageSanitizer.redact(output);
        execution.setOutput(safeOutput == null ? null : safeOutput.substring(0, Math.min(100000, safeOutput.length())));
        execution.setFinishedAt(LocalDateTime.now());
        execution.setDurationMs(durationMs);
        execution.setPromptTokens(promptTokens);
        execution.setCompletionTokens(completionTokens);
        execution.setTotalTokens(totalTokens);
        execution.setToolCallCount(toolCallCount);
        execution.setToolCalls(toolCalls);
        eventPublisher.publish(AgentEvent.execution(
                status == ExecutionStatus.COMPLETED
                        ? AgentEventType.AGENT_EXECUTED : AgentEventType.AGENT_EXECUTION_FAILED,
                "agent-service",
                execution.getAgentId(),
                execution.getVersionId(),
                execution.getId(),
                status.name(),
                durationMs,
                promptTokens,
                completionTokens,
                totalTokens,
                toolCallCount,
                errorCode
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStale(LocalDateTime before) {
        for (AgentExecution execution : repository.findByStatusAndStartedAtBefore(ExecutionStatus.RUNNING, before)) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorCode("SERVICE_RESTART");
            execution.setErrorMessage("L'exécution a été interrompue par le redémarrage du service.");
            execution.setFinishedAt(LocalDateTime.now());
            execution.setDurationMs(Math.max(0,
                    java.time.Duration.between(execution.getStartedAt(), execution.getFinishedAt()).toMillis()));
            eventPublisher.publish(AgentEvent.execution(
                    AgentEventType.AGENT_EXECUTION_FAILED,
                    "agent-service",
                    execution.getAgentId(),
                    execution.getVersionId(),
                    execution.getId(),
                    ExecutionStatus.FAILED.name(),
                    execution.getDurationMs(),
                    null,
                    null,
                    null,
                    execution.getToolCallCount(),
                    "SERVICE_RESTART"
            ));
        }
    }

    private String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Impossible de sérialiser les paramètres d'exécution.", exception);
        }
    }
}
