package com.example.anomalydetection.api;

public record AnomalyCheckRequest(Long executionId, Long agentId, Long agentVersionId, long durationMs,
                                  int tokensUsed, int retryCount, String executionStatus, Long modelVersionId) {
}
