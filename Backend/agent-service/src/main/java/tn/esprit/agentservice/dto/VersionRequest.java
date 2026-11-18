package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

import java.util.Map;

public record VersionRequest(
        String model,
        String systemPrompt,
        String changeLog,
        @Valid ConfigurationDto configuration,
        Long basedOnVersionId,
        String source,
        Long studyId,
        Map<String, Object> params
) {

    @AssertTrue(message = "model, systemPrompt et configuration sont requis pour une nouvelle version UI.")
    @JsonIgnore
    public boolean isValidPayloadShape() {
        return basedOnVersionId != null
                || (model != null && !model.isBlank()
                && systemPrompt != null && !systemPrompt.isBlank()
                && configuration != null);
    }
}
