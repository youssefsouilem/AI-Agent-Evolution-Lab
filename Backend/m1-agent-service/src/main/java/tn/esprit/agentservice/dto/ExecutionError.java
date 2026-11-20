package tn.esprit.agentservice.dto;

public record ExecutionError(
        Long executionId,
        Long versionId,
        Long durationMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        int toolCallCount
) {
}
