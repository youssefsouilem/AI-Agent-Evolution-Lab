package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LlmProviderDto(
        String id,
        String provider,
        String label,
        boolean available,
        String reason,
        boolean supportsTools,
        List<Model> models
) {
    public record Model(String id, String label, boolean recommended) { }
}
