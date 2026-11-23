package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record MeResponse(
        String userId,
        String displayName,
        String role,
        Limits limits,
        Usage usage,
        Capabilities capabilities
) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Limits(Integer maxAgents) { }
    public record Usage(long agents) { }
    public record Capabilities(
            boolean history,
            boolean overrides,
            boolean assist,
            boolean llmCatalog,
            boolean stats,
            boolean quota
    ) { }
}
