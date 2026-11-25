package tn.esprit.agentservice.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.AgentStats;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.repository.AgentExecutionRepository;

import java.time.LocalDateTime;

@Service
public class AgentStatsService {
    private final AgentService agents;
    private final AgentAccessPolicy accessPolicy;
    private final AgentExecutionRepository executions;

    public AgentStatsService(
            AgentService agents,
            AgentAccessPolicy accessPolicy,
            AgentExecutionRepository executions
    ) {
        this.agents = agents;
        this.accessPolicy = accessPolicy;
        this.executions = executions;
    }

    @Transactional(readOnly = true)
    public AgentStats get(Long agentId, int days, CurrentUser user) {
        Agent agent = agents.loadOwnedAgent(agentId, user);
        accessPolicy.requireRead(agent, user);
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        var aggregate = executions.aggregateStats(agentId, since);
        long total = value(aggregate.getTotal());
        long completed = value(aggregate.getCompleted());
        long failed = value(aggregate.getFailed());
        Long p95 = null;
        long durationCount = executions.countByAgentIdAndStartedAtGreaterThanEqualAndDurationMsIsNotNull(
                agentId, since);
        if (durationCount > 0) {
            int offset = (int) Math.ceil(durationCount * 0.95) - 1;
            var duration = executions.orderedDurations(agentId, since, PageRequest.of(Math.max(0, offset), 1));
            p95 = duration.isEmpty() ? null : duration.get(0);
        }
        Long avg = aggregate.getAvgDurationMs() == null ? null : Math.round(aggregate.getAvgDurationMs());
        return new AgentStats(days, total, completed, failed,
                total == 0 ? null : (double) completed / total,
                avg,
                p95,
                aggregate.getTotalTokensKnown(),
                value(aggregate.getUnknownTokens()),
                executions.dailyStats(agentId, since).stream()
                        .map(day -> new AgentStats.Day(day.getDayDate(), value(day.getTotal()),
                                value(day.getFailed()), day.getAvgDurationMs() == null
                                ? null : Math.round(day.getAvgDurationMs()), day.getTokens()))
                        .toList());
    }

    private long value(Long number) {
        return number == null ? 0 : number;
    }
}
