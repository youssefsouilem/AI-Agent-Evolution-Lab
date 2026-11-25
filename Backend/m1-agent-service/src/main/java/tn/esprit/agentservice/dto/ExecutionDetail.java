package tn.esprit.agentservice.dto;

import tn.esprit.agentservice.entity.ExecutionStatus;

import java.time.LocalDateTime;

public record ExecutionDetail(
        Long id,
        Long agentId,
        Long versionId,
        int versionNumber,
        String requestedBy,
        String input,
        String context,
        String output,
        String errorCode,
        String errorMessage,
        ExecutionStatus status,
        String overrides,
        String appliedSettings,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        int toolCallCount,
        String toolCalls,
        String provider,
        String modelUsed
) {
}
