package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StructuredExecutionError(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        ExecutionError execution,
        List<FieldError> fieldErrors
) {
}
