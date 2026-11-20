package tn.esprit.agentservice.dto;

import java.util.List;
import java.util.Map;

public record ExecuteResponse(
        Long executionId,
        String status,
        String output,
        Integer tokensUsed,
        Integer promptTokens,
        Integer completionTokens,
        long durationMs,
        Long versionId,
        int toolCallCount,
        Map<String, Object> overrides,
        Map<String, Object> appliedSettings
) {
}
