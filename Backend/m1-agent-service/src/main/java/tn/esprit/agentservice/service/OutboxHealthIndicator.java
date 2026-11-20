package tn.esprit.agentservice.service;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.repository.OutboxEventRepository;

@Component("events")
public class OutboxHealthIndicator implements HealthIndicator {
    private final OutboxEventRepository repository;

    public OutboxHealthIndicator(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        long pending = repository.countByStatus(OutboxStatus.PENDING);
        long failed = repository.countByStatus(OutboxStatus.FAILED);
        Health.Builder result = failed > 0 ? Health.down() : Health.up();
        return result.withDetail("pending", pending).withDetail("failed", failed).build();
    }
}
