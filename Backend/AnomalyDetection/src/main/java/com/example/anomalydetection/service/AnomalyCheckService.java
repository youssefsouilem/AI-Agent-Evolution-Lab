package com.example.anomalydetection.service;

import com.example.anomalydetection.api.AnomalyCheckRequest;
import com.example.anomalydetection.domain.Alert;
import com.example.anomalydetection.domain.AnomalyCheck;
import com.example.anomalydetection.domain.AnomalyReason;
import com.example.anomalydetection.domain.DetectionSource;
import com.example.anomalydetection.domain.ModelVersion;
import com.example.anomalydetection.domain.Severity;
import com.example.anomalydetection.repository.AnomalyCheckRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnomalyCheckService {
    private final AnomalyCheckRepository repository;
    private final ModelVersionService models;
    public AnomalyCheckService(AnomalyCheckRepository repository, ModelVersionService models) { this.repository = repository; this.models = models; }
    public List<AnomalyCheck> findAll() { return repository.findAll(); }
    public AnomalyCheck findById(Long id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Anomaly check not found: " + id)); }
    @Transactional
    public AnomalyCheck create(AnomalyCheckRequest r) {
        validate(r);
        ModelVersion model = r.modelVersionId() == null ? models.active().stream().findFirst().orElse(null) : models.findById(r.modelVersionId());
        Assessment assessment = assess(r, model);
        AnomalyCheck check = new AnomalyCheck(r.executionId(), r.agentId(), r.agentVersionId(), r.durationMs(), r.tokensUsed(),
                r.retryCount(), r.executionStatus(), assessment.score(), assessment.detected(), assessment.severity(),
                assessment.reason(), assessment.blocking(), assessment.source(), model);
        if (assessment.detected()) check.setAlert(new Alert(assessment.severity(), assessment.reason().name().replace('_', ' '), check));
        return repository.save(check);
    }
    @Transactional
    public AnomalyCheck update(Long id, AnomalyCheckRequest r) {
        AnomalyCheck check = findById(id); validate(r);
        check.update(r.agentId(), r.agentVersionId(), r.durationMs(), r.tokensUsed(), r.retryCount(), r.executionStatus());
        return check;
    }
    @Transactional
    public void delete(Long id) { repository.delete(findById(id)); }
    public List<AnomalyCheck> byAgent(Long id) { return repository.findByAgentIdOrderByCheckedAtDesc(id); }
    public List<AnomalyCheck> byExecution(Long id) { return repository.findByExecutionIdOrderByCheckedAtDesc(id); }
    public List<AnomalyCheck> detected() { return repository.findByAnomalyDetectedTrueOrderByCheckedAtDesc(); }
    public List<AnomalyCheck> between(LocalDateTime from, LocalDateTime to) { return repository.findByCheckedAtBetweenOrderByCheckedAtDesc(from, to); }
    public Map<String, Long> summary() { return Map.of("totalChecks", repository.count(), "detectedAnomalies", repository.countByAnomalyDetectedTrue(), "minor", repository.countBySeverity(Severity.MINOR), "critical", repository.countBySeverity(Severity.CRITICAL)); }
    private void validate(AnomalyCheckRequest r) {
        if (r.executionId() == null || r.agentId() == null || r.executionStatus() == null || r.executionStatus().isBlank()) throw new IllegalArgumentException("Execution id, agent id and execution status are required");
        if (r.durationMs() < 0 || r.tokensUsed() < 0 || r.retryCount() < 0) throw new IllegalArgumentException("Metrics cannot be negative");
    }
    private Assessment assess(AnomalyCheckRequest r, ModelVersion model) {
        if (!"SUCCESS".equalsIgnoreCase(r.executionStatus())) return new Assessment(1.0, true, Severity.CRITICAL, AnomalyReason.EXECUTION_FAILURE, true, DetectionSource.RULE);
        if (r.retryCount() >= 3) return new Assessment(.85, true, Severity.CRITICAL, AnomalyReason.EXCESSIVE_RETRIES, true, DetectionSource.RULE);
        if (r.durationMs() > 30_000) return new Assessment(.75, true, Severity.MINOR, AnomalyReason.HIGH_LATENCY, false, DetectionSource.RULE);
        if (r.tokensUsed() > 100_000) return new Assessment(.70, true, Severity.MINOR, AnomalyReason.HIGH_TOKEN_USAGE, false, DetectionSource.RULE);
        double score = model != null && model.getThresholdScore() != null ? model.getThresholdScore() / 2 : 0.1;
        return new Assessment(score, false, Severity.NONE, AnomalyReason.NONE, false, model == null ? DetectionSource.RULE : DetectionSource.ISOLATION_FOREST);
    }
    private record Assessment(double score, boolean detected, Severity severity, AnomalyReason reason, boolean blocking, DetectionSource source) {}
}
