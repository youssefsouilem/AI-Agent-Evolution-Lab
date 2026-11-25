package tn.esprit.agentservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.entity.UserQuota;
import tn.esprit.agentservice.exception.AgentQuotaExceededException;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.UserQuotaRepository;

@Service
public class UserQuotaService {
    private final UserQuotaRepository quotas;
    private final AgentRepository agents;
    private final int defaultMaxAgents;

    public UserQuotaService(
            UserQuotaRepository quotas,
            AgentRepository agents,
            @Value("${m1.quota.user-max-agents:5}") int defaultMaxAgents
    ) {
        this.quotas = quotas;
        this.agents = agents;
        this.defaultMaxAgents = defaultMaxAgents;
    }

    @Transactional
    public void checkCreation(String ownerId) {
        quotas.ensureRow(ownerId);
        UserQuota quota = quotas.findByOwnerIdForUpdate(ownerId).orElseThrow();
        int limit = quota.getMaxAgents() == null ? defaultMaxAgents : quota.getMaxAgents();
        long usage = agents.countByOwnerId(ownerId);
        if (usage >= limit) {
            throw new AgentQuotaExceededException(limit, usage);
        }
    }

    @Transactional(readOnly = true)
    public Integer effectiveLimit(String ownerId, boolean admin) {
        if (admin) {
            return null;
        }
        return quotas.findById(ownerId).map(UserQuota::getMaxAgents).orElse(defaultMaxAgents);
    }

    @Transactional(readOnly = true)
    public Integer effectiveLimitForOwner(String ownerId) {
        return quotas.findById(ownerId).map(UserQuota::getMaxAgents).filter(value -> value > 0)
                .orElse(defaultMaxAgents);
    }

    public int defaultLimit() {
        return defaultMaxAgents;
    }

    @Transactional
    public UserQuota setOverride(String ownerId, Integer maxAgents, String updatedBy) {
        UserQuota quota = quotas.findById(ownerId).orElseGet(() -> {
            UserQuota created = new UserQuota();
            created.setOwnerId(ownerId);
            return created;
        });
        quota.setMaxAgents(maxAgents);
        quota.setUpdatedBy(updatedBy);
        return quotas.save(quota);
    }

    @Transactional(readOnly = true)
    public boolean hasOverride(String ownerId) {
        return quotas.findById(ownerId).map(UserQuota::getMaxAgents).map(value -> true).orElse(false);
    }
}
