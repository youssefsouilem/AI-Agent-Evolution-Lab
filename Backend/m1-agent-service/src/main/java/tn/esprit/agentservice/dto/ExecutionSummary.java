package tn.esprit.agentservice.dto;

import tn.esprit.agentservice.entity.ExecutionStatus;

import java.time.LocalDateTime;

public record ExecutionSummary(
        Long id,
        Long agentId,
        Long versionId,
        int versionNumber,
        ExecutionStatus status,
        String errorCode,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        Integer totalTokens,
        String inputPreview,
        boolean hasOverrides
) {
}
