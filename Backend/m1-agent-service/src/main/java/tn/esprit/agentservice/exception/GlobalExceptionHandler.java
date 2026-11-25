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
import tn.esprit.agentservice.dto.FieldError;
import tn.esprit.agentservice.dto.StructuredExecutionError;
import tn.esprit.agentservice.service.ErrorMessageSanitizer;
import tn.esprit.agentservice.service.LlmFailureClassifier;
import tn.esprit.agentservice.service.OpenAiCompatibleLlmGateway;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final LlmFailureClassifier failureClassifier;

    public GlobalExceptionHandler(LlmFailureClassifier failureClassifier) {
        this.failureClassifier = failureClassifier;
    }

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
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request, "MODEL_NOT_AVAILABLE",
                null, null, List.of(new FieldError("model", exception.getMessage())));
    }

    @ExceptionHandler(ModelAmbiguousException.class)
    public ResponseEntity<ApiError> handleAmbiguousModel(
            ModelAmbiguousException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request, "MODEL_AMBIGUOUS",
                null, null, List.of(new FieldError("provider", exception.getMessage())));
    }

    @ExceptionHandler(ProviderNotConfiguredException.class)
    public ResponseEntity<ApiError> handleProviderNotConfigured(
            ProviderNotConfiguredException exception,
            HttpServletRequest request
    ) {
        return codedError(HttpStatus.BAD_GATEWAY, exception.getMessage(), "PROVIDER_NOT_CONFIGURED", request);
    }

    @ExceptionHandler(OpenAiCompatibleLlmGateway.ProviderHttpException.class)
    public ResponseEntity<ApiError> handleProviderHttpError(
            OpenAiCompatibleLlmGateway.ProviderHttpException exception,
            HttpServletRequest request
    ) {
        LlmFailureClassifier.Failure failure = failureClassifier.classify(exception);
        return codedError(failure.status(), failure.message(), failure.code(), request);
    }

    @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class)
    public ResponseEntity<ApiError> handleProviderNetworkError(
            org.springframework.web.client.ResourceAccessException exception,
            HttpServletRequest request
    ) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).warn(
                "Appel au fournisseur impossible pour {} : {}", request.getRequestURI(),
                ErrorMessageSanitizer.sanitize(String.valueOf(exception.getMessage())));
        LlmFailureClassifier.Failure failure = failureClassifier.classify(exception);
        return codedError(failure.status(), failure.message(), failure.code(), request);
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
        String message = ErrorMessageSanitizer.sanitize(exception.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, message, request, null, null, null,
                List.of(new FieldError(configurationField(message), message)));
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
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(fieldName(error.getField()), validationMessage(error.getCodes())))
                .toList();
        String message = fieldErrors.isEmpty() ? "Le corps de la requête est invalide."
                : fieldErrors.get(0).field() + " " + fieldErrors.get(0).message();
        return error(HttpStatus.BAD_REQUEST, message, request, null, null, null, fieldErrors);
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
                        exception.getToolCallCount()),
                status.value() == HttpStatus.UNPROCESSABLE_ENTITY.value() && isAgentRequest(request)
                        ? executionFieldErrors(exception) : null
        ));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleUnreadableRequest(Exception exception, HttpServletRequest request) {
        List<FieldError> fieldErrors;
        if (exception instanceof ConstraintViolationException violations) {
            fieldErrors = violations.getConstraintViolations().stream()
                    .map(violation -> new FieldError(violation.getPropertyPath().toString(),
                            "n'a pas une valeur autorisée."))
                    .toList();
        } else if (exception instanceof MissingServletRequestParameterException missing) {
            fieldErrors = List.of(new FieldError(missing.getParameterName(), "est obligatoire."));
        } else {
            fieldErrors = List.of(new FieldError("body", "Le corps ou les paramètres sont invalides."));
        }
        return error(HttpStatus.BAD_REQUEST, "Le corps ou les paramètres de la requête sont invalides.",
                request, null, null, null, fieldErrors);
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
        return error(status, message, request, errorCode, limit, usage, null);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            String errorCode,
            Integer limit,
            Long usage,
            List<FieldError> fieldErrors
    ) {
        if (fieldErrors == null && (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNPROCESSABLE_ENTITY)
                && isAgentRequest(request)) {
            fieldErrors = List.of();
        }
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                errorCode,
                limit,
                usage,
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }

    private boolean isAgentRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/agents");
    }

    private List<FieldError> executionFieldErrors(ExecutionFailedException exception) {
        if ("MODEL_NOT_AVAILABLE".equals(exception.getErrorCode())) {
            return List.of(new FieldError("model", ErrorMessageSanitizer.sanitize(exception.getMessage())));
        }
        return List.of();
    }

    private String fieldName(String field) {
        return "validPayloadShape".equals(field) ? "model" : field;
    }

    private String validationMessage(String[] codes) {
        String joined = String.join(" ", codes).toLowerCase(Locale.ROOT);
        if (joined.contains("notblank")) {
            return "ne doit pas être vide.";
        }
        if (joined.contains("notnull")) {
            return "est obligatoire.";
        }
        return "n'a pas une valeur autorisée.";
    }

    private String configurationField(String message) {
        int separator = message.indexOf(' ');
        String field = separator < 0 ? message : message.substring(0, separator);
        if (field.matches("[A-Za-z][A-Za-z0-9.]*")) {
            return field.startsWith("configuration.") ? field : "configuration." + field;
        }
        return "configuration";
    }

    private ResponseEntity<ApiError> codedError(
            HttpStatus status, String message, String code, HttpServletRequest request
    ) {
        return error(status, message, request, code, null, null);
    }
}
