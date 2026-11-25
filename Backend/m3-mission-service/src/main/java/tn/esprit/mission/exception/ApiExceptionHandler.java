package tn.esprit.mission.exception;

import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(org.springframework.security.access.AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> missing(
        ResourceNotFoundException ex
    ) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalid(
        IllegalArgumentException ex
    ) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(
        MethodArgumentNotValidException ex
    ) {
        String message = ex
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .distinct()
            .reduce((a, b) -> a + "; " + b)
            .orElse("Requete invalide");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(AgentCatalogUnavailableException.class)
    public ResponseEntity<Map<String, Object>> catalogueUnavailable(AgentCatalogUnavailableException ex) {
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(ExecutionConflictException.class)
    public ResponseEntity<Map<String, Object>> executionConflict(ExecutionConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MonitoringUnavailableException.class)
    public ResponseEntity<Map<String, Object>> monitoringUnavailable(MonitoringUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(MonitoringContractException.class)
    public ResponseEntity<Map<String, Object>> monitoringContract(MonitoringContractException ex) {
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(
        HttpStatus status,
        String message
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", Instant.now().toString());
        data.put("status", status.value());
        data.put("error", status.getReasonPhrase());
        data.put("message", message);
        return ResponseEntity.status(status).body(data);
    }
}
