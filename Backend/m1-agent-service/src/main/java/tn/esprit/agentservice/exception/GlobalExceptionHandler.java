package tn.esprit.agentservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tn.esprit.agentservice.dto.ApiError;
import tn.esprit.agentservice.dto.ExecutionError;
import tn.esprit.agentservice.dto.StructuredExecutionError;
import tn.esprit.agentservice.service.ErrorMessageSanitizer;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, ErrorMessageSanitizer.sanitize(exception.getMessage()), request);
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof ConflictException
                ? ErrorMessageSanitizer.sanitize(exception.getMessage())
                : "Cette modification entre en conflit avec les données existantes.";
        return error(HttpStatus.CONFLICT, message, request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbidden(
            ForbiddenOperationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request, exception.getErrorCode(), null, null);
    }

    @ExceptionHandler(AgentQuotaExceededException.class)
    public ResponseEntity<ApiError> handleQuotaExceeded(
            AgentQuotaExceededException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request, "AGENT_QUOTA_EXCEEDED",
                exception.getLimit(), exception.getUsage());
    }

    @ExceptionHandler(ModelNotAvailableException.class)
    public ResponseEntity<ApiError> handleModelUnavailable(
            ModelNotAvailableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request, "MODEL_NOT_AVAILABLE", null, null);
    }

    @ExceptionHandler(ModelAmbiguousException.class)
    public ResponseEntity<ApiError> handleAmbiguousModel(
            ModelAmbiguousException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request, "MODEL_AMBIGUOUS", null, null);
    }

    @ExceptionHandler(ProviderNotConfiguredException.class)
    public ResponseEntity<ApiError> handleProviderNotConfigured(
            ProviderNotConfiguredException exception,
            HttpServletRequest request
    ) {
        return codedError(HttpStatus.BAD_GATEWAY, exception.getMessage(), "PROVIDER_NOT_CONFIGURED", request);
    }

    @ExceptionHandler(AssistInvalidOutputException.class)
    public ResponseEntity<ApiError> handleAssistInvalidOutput(
            AssistInvalidOutputException exception,
            HttpServletRequest request
    ) {
        return codedError(HttpStatus.BAD_GATEWAY, exception.getMessage(), "ASSIST_INVALID_OUTPUT", request);
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ApiError> handleRateLimited(
            RateLimitedException exception,
            HttpServletRequest request
    ) {
        return codedError(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), "RATE_LIMITED", request);
    }

    @ExceptionHandler(InvalidConfigurationException.class)
    public ResponseEntity<ApiError> handleInvalidConfiguration(
            InvalidConfigurationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ErrorMessageSanitizer.sanitize(exception.getMessage()), request);
    }

    @ExceptionHandler(DownstreamTimeoutException.class)
    public ResponseEntity<ApiError> handleDownstreamTimeout(
            DownstreamTimeoutException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.GATEWAY_TIMEOUT, "Le service en aval a dépassé le délai maximal.", request);
    }

    @ExceptionHandler({DownstreamServiceException.class, AgentExecutionException.class})
    public ResponseEntity<ApiError> handleDownstreamFailure(RuntimeException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        String message = "Un service en aval est indisponible.";
        if (exception instanceof AgentExecutionException executionException) {
            status = executionException.getStatus();
            message = "L'exécution de l'agent a échoué.";
        }
        return error(status, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Le corps de la requête est invalide.");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ExecutionFailedException.class)
    public ResponseEntity<StructuredExecutionError> handleExecutionFailed(
            ExecutionFailedException exception, HttpServletRequest request
    ) {
        HttpStatus status = exception.getHttpStatus();
        return ResponseEntity.status(status).body(new StructuredExecutionError(
                Instant.now(), status.value(), status.getReasonPhrase(), exception.getErrorCode(),
                ErrorMessageSanitizer.sanitize(exception.getMessage()), request.getRequestURI(),
                new ExecutionError(exception.getExecutionId(), exception.getVersionId(), exception.getDurationMs(),
                        exception.getPromptTokens(), exception.getCompletionTokens(), exception.getTotalTokens(),
                        exception.getToolCallCount())
        ));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleUnreadableRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Le corps ou les paramètres de la requête sont invalides.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        RuntimeException safeTrace = new RuntimeException(ErrorMessageSanitizer.sanitize(
                exception.getClass().getSimpleName() + ": " + exception.getMessage()));
        safeTrace.setStackTrace(exception.getStackTrace());
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unexpected request failure for {}.", request.getRequestURI(), safeTrace);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue.", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        return error(status, message, request, null, null, null);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            String errorCode,
            Integer limit,
            Long usage
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                errorCode,
                limit,
                usage
        );
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ApiError> codedError(
            HttpStatus status, String message, String code, HttpServletRequest request
    ) {
        return error(status, message, request, code, null, null);
    }
}
