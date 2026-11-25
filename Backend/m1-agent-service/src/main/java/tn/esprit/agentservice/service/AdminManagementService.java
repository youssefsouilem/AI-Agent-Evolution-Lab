package tn.esprit.agentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.AdminAuditResponse;
import tn.esprit.agentservice.dto.AdminUserSummary;
import tn.esprit.agentservice.entity.AdminAudit;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.UserQuota;
import tn.esprit.agentservice.exception.ForbiddenOperationException;
import tn.esprit.agentservice.repository.AdminAuditRepository;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminManagementService {
    private final AgentService agentService;
    private final AgentRepository agents;
    private final AgentExecutionRepository executions;
    private final UserQuotaService quotas;
    private final AdminAuditRepository audit;
    private final ObjectMapper objectMapper;

    public AdminManagementService(
            AgentService agentService,
            AgentRepository agents,
            AgentExecutionRepository executions,
            UserQuotaService quotas,
            AdminAuditRepository audit,
            ObjectMapper objectMapper
    ) {
        this.agentService = agentService;
        this.agents = agents;
        this.executions = executions;
        this.quotas = quotas;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummary> users(CurrentUser actor) {
        requireAdmin(actor);
        List<Agent> rows = agents.findAll();
        Map<String, List<Agent>> byOwner = new HashMap<>();
        rows.forEach(agent -> byOwner.computeIfAbsent(agent.getOwnerId(), ignored -> new ArrayList<>()).add(agent));
        Map<String, LocalDateTime> lastActivity = new HashMap<>();
        rows.forEach(agent -> {
            if (agent.getUpdatedAt() != null) {
                lastActivity.merge(agent.getOwnerId(), agent.getUpdatedAt(), this::later);
            }
        });
        executions.latestActivityByUser().forEach(activity -> {
            if (activity.getLatest() != null) {
                lastActivity.merge(activity.getOwnerId(), activity.getLatest(), this::later);
            }
        });
        return byOwner.entrySet().stream().map(entry -> {
            List<Agent> owned = entry.getValue();
            return new AdminUserSummary(entry.getKey(), owned.size(),
                    owned.stream().filter(agent -> agent.getStatus() == AgentStatus.ACTIVE).count(),
                    quotas.effectiveLimitForOwner(entry.getKey()), quotas.hasOverride(entry.getKey()),
                    lastActivity.get(entry.getKey()));
        }).toList();
    }

    @Transactional
    public void setQuota(String ownerId, Integer maxAgents, CurrentUser actor) {
        requireAdmin(actor);
        quotas.setOverride(ownerId, maxAgents, actor.id());
        Map<String, Object> details = new HashMap<>();
        details.put("maxAgents", maxAgents);
        writeAudit(actor, "QUOTA_CHANGED", null, ownerId, null, details);
    }

    @Transactional
    public void archive(Long agentId, String reason, CurrentUser actor) {
        requireAdmin(actor);
        Agent agent = agentService.findForAdmin(agentId);
        agentService.archiveForAdmin(agent, actor, reason);
        writeAudit(actor, "AGENT_ARCHIVED", agentId, agent.getOwnerId(), reason, Map.of());
    }

    @Transactional
    public void delete(Long agentId, String reason, CurrentUser actor) {
        requireAdmin(actor);
        Agent agent = agentService.findForAdmin(agentId);
        String ownerId = agent.getOwnerId();
        agentService.deleteForAdmin(agent, actor, reason);
        writeAudit(actor, "AGENT_DELETED", agentId, ownerId, reason, Map.of());
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditResponse> auditPage(int page, int size, CurrentUser actor) {
        requireAdmin(actor);
        return audit.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AdminAuditResponse::from);
    }

    private void writeAudit(
            CurrentUser actor, String action, Long agentId, String ownerId, String reason, Map<String, ?> details
    ) {
        AdminAudit row = new AdminAudit();
        row.setActorId(actor.id());
        row.setAction(action);
        row.setTargetAgentId(agentId);
        row.setTargetOwnerId(ownerId);
        row.setReason(reason);
        try {
            row.setDetails(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de sérialiser l'audit administratif.", exception);
        }
        audit.save(row);
    }

    private void requireAdmin(CurrentUser actor) {
        if (!actor.admin()) {
            throw new ForbiddenOperationException("Rôle administrateur requis.");
        }
    }

    private LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }
}
