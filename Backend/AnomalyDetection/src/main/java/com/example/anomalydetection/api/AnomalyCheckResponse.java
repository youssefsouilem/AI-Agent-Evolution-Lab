package com.example.anomalydetection.api;

import com.example.anomalydetection.domain.AnomalyCheck;
import java.time.LocalDateTime;

public record AnomalyCheckResponse(Long id, Long executionId, Long agentId, Long agentVersionId, long durationMs,
                                   int tokensUsed, int retryCount, String executionStatus, Double anomalyScore,
                                   boolean anomalyDetected, String severity, String reason, boolean blocking,
                                   String source, Long modelVersionId, Long alertId, LocalDateTime checkedAt) {
    public static AnomalyCheckResponse from(AnomalyCheck c) {
        return new AnomalyCheckResponse(c.getId(), c.getExecutionId(), c.getAgentId(), c.getAgentVersionId(),
                c.getDurationMs(), c.getTokensUsed(), c.getRetryCount(), c.getExecutionStatus(), c.getAnomalyScore(),
                c.isAnomalyDetected(), c.getSeverity().name(), c.getReason().name(), c.isBlocking(),
                c.getSource().name(), c.getModelVersion() == null ? null : c.getModelVersion().getId(),
                c.getAlert() == null ? null : c.getAlert().getId(), c.getCheckedAt());
    }
}
