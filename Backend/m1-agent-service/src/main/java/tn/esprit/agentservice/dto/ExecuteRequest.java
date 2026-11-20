package tn.esprit.agentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ExecuteRequest(
        @Positive Long versionId,
        @NotBlank @Size(max = 20000) String input,
        JsonNode context,
        Map<String, Object> overrides
) {
    @AssertTrue(message = "context doit être une chaîne ou un objet JSON.")
    @JsonIgnore
    public boolean isContextValid() {
        return context == null || context.isNull() || context.isTextual() || context.isObject();
    }
}
