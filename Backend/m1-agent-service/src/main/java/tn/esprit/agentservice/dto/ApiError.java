package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String errorCode,
        Integer limit,
        Long usage,
        List<FieldError> fieldErrors
) {
    public ApiError(
            Instant timestamp, int status, String error, String message, String path,
            String errorCode, Integer limit, Long usage
    ) {
        this(timestamp, status, error, message, path, errorCode, limit, usage, null);
    }

    public ApiError(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null, null, null, null);
    }
}
