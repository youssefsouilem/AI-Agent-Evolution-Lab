package tn.esprit.agentservice.exception;

public class ForbiddenOperationException extends RuntimeException {
    private final String errorCode;

    public ForbiddenOperationException(String message) {
        this(message, null);
    }

    public ForbiddenOperationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
