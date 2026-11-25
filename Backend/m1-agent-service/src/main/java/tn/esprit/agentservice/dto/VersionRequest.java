package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record VersionRequest(
        String provider,
        String model,
        String systemPrompt,
        String changeLog,
        @Valid ConfigurationDto configuration,
        Long basedOnVersionId,
        @Pattern(regexp = "USER|M5") String source,
        Long studyId,
        Map<String, Object> params
) {
    public VersionRequest(
            String model,
            String systemPrompt,
            String changeLog,
            ConfigurationDto configuration,
            Long basedOnVersionId,
            String source,
            Long studyId,
            Map<String, Object> params
    ) {
        this(null, model, systemPrompt, changeLog, configuration, basedOnVersionId, source, studyId, params);
    }

    @AssertTrue(message = "model, systemPrompt et configuration sont requis pour une nouvelle version UI.")
    @JsonIgnore
    public boolean isValidPayloadShape() {
        return basedOnVersionId != null
                || (model != null && !model.isBlank()
                && systemPrompt != null && !systemPrompt.isBlank()
                && configuration != null);
    }
}
