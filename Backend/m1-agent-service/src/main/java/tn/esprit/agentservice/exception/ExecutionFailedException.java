package tn.esprit.agentservice.exception;

import org.springframework.http.HttpStatus;

public class ExecutionFailedException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Long executionId;
    private final Long versionId;
    private final Long durationMs;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final int toolCallCount;

    public ExecutionFailedException(
            String errorCode,
            HttpStatus httpStatus,
            String message,
            Long executionId,
            Long versionId,
            Long durationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            int toolCallCount,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.executionId = executionId;
        this.versionId = versionId;
        this.durationMs = durationMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.toolCallCount = toolCallCount;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
    public Long getExecutionId() { return executionId; }
    public Long getVersionId() { return versionId; }
    public Long getDurationMs() { return durationMs; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public int getToolCallCount() { return toolCallCount; }
}
