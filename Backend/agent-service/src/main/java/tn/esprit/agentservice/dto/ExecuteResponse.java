package tn.esprit.agentservice.dto;

public record ExecuteResponse(
        String status,
        String output,
        long tokensUsed,
        long durationMs,
        Long versionId
) {
}
