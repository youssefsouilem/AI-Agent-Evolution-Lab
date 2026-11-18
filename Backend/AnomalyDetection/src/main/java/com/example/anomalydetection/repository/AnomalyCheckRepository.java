package com.example.anomalydetection.repository;

import com.example.anomalydetection.domain.AnomalyCheck;
import com.example.anomalydetection.domain.Severity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyCheckRepository extends JpaRepository<AnomalyCheck, Long> {
    List<AnomalyCheck> findByAgentIdOrderByCheckedAtDesc(Long agentId);
    List<AnomalyCheck> findByExecutionIdOrderByCheckedAtDesc(Long executionId);
    List<AnomalyCheck> findByAnomalyDetectedTrueOrderByCheckedAtDesc();
    long countByAnomalyDetectedTrue();
    long countBySeverity(Severity severity);
    List<AnomalyCheck> findByCheckedAtBetweenOrderByCheckedAtDesc(LocalDateTime from, LocalDateTime to);
}
