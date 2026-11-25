package tn.esprit.agentservice.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.ExecutionDetail;
import tn.esprit.agentservice.dto.ExecutionSummary;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.exception.AgentNotFoundException;
import tn.esprit.agentservice.exception.ResourceNotFoundException;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentRepository;

import java.util.List;

@Service
public class AgentExecutionHistoryService {
    private final AgentService agentService;
    private final AgentAccessPolicy accessPolicy;
    private final AgentExecutionRepository executions;
    private final AgentRepository agents;
    @Autowired
    public AgentExecutionHistoryService(
            AgentService agentService,
            AgentExecutionRepository executions,
            AgentRepository agents
    ) {
        this(agentService, new AgentAccessPolicy(), executions, agents);
    }

    public AgentExecutionHistoryService(
            AgentService agentService,
            AgentAccessPolicy accessPolicy,
            AgentExecutionRepository executions,
            AgentRepository agents
    ) {
        this.agentService = agentService;
        this.accessPolicy = accessPolicy;
        this.executions = executions;
        this.agents = agents;
    }

    @Transactional(readOnly = true)
    public HistoryPage forAgent(
            Long agentId, ExecutionStatus status, Long versionId, int page, int size, CurrentUser user
    ) {
        accessPolicy.requireRead(agentService.loadOwnedAgent(agentId, user), user);
        Specification<AgentExecution> spec = Specification.where((root, query, cb) ->
                cb.equal(root.get("agentId"), agentId));
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (versionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("versionId"), versionId));
        }
        var result = executions.findAll(spec, PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("startedAt").descending()));
        return new HistoryPage(result.getContent().stream().map(this::summary).toList(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ExecutionDetail detail(Long agentId, Long executionId, CurrentUser user) {
        accessPolicy.requireRead(agentService.loadOwnedAgent(agentId, user), user);
        AgentExecution execution = executions.findById(executionId)
                .filter(row -> row.getAgentId().equals(agentId))
                .orElseThrow(() -> new ResourceNotFoundException("L'exécution demandée est introuvable."));
        return detail(execution);
    }

    @Transactional(readOnly = true)
    public HistoryPage acrossAgents(Long agentId, ExecutionStatus status, int page, int size, boolean all, CurrentUser user) {
        List<Long> allowedAgentIds;
        if (all && user.admin()) {
            allowedAgentIds = agents.findAll().stream().map(agent -> agent.getId()).toList();
        } else {
            allowedAgentIds = agents.findByOwnerId(user.id()).stream().map(agent -> agent.getId()).toList();
        }
        if (agentId != null) {
            if (!allowedAgentIds.contains(agentId)) {
                throw new AgentNotFoundException(agentId);
            }
            allowedAgentIds = List.of(agentId);
        }
        if (allowedAgentIds.isEmpty()) {
            return new HistoryPage(List.of(), 0);
        }
        List<Long> visibleAgentIds = List.copyOf(allowedAgentIds);
        Specification<AgentExecution> spec = (root, query, cb) ->
                root.get("agentId").in(visibleAgentIds);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        var result = executions.findAll(spec, PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("startedAt").descending()));
        return new HistoryPage(result.getContent().stream().map(this::summary).toList(), result.getTotalElements());
    }

    private ExecutionSummary summary(AgentExecution row) {
        return new ExecutionSummary(row.getId(), row.getAgentId(), row.getVersionId(), row.getVersionNumber(),
                row.getStatus(), row.getErrorCode(), row.getStartedAt(), row.getFinishedAt(), row.getDurationMs(),
                row.getTotalTokens(), row.getInput().substring(0, Math.min(200, row.getInput().length())),
                row.getOverrides() != null && !row.getOverrides().equals("{}"));
    }

    private ExecutionDetail detail(AgentExecution row) {
        return new ExecutionDetail(row.getId(), row.getAgentId(), row.getVersionId(), row.getVersionNumber(),
                row.getRequestedBy(), row.getInput(), row.getContext(), row.getOutput(), row.getErrorCode(),
                row.getErrorMessage(), row.getStatus(), row.getOverrides(), row.getAppliedSettings(),
                row.getStartedAt(), row.getFinishedAt(), row.getDurationMs(), row.getPromptTokens(),
                row.getCompletionTokens(), row.getTotalTokens(), row.getToolCallCount(), row.getToolCalls(),
                row.getProvider(), row.getModelUsed());
    }

    public record HistoryPage(List<ExecutionSummary> items, long total) { }
}
