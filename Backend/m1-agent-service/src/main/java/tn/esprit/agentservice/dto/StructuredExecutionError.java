package tn.esprit.agentservice.dto;

import java.time.Instant;

public record StructuredExecutionError(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        ExecutionError execution
) {
}
