package tn.esprit.agentservice.exception;

public class ExecutionLimitException extends RuntimeException {
    public ExecutionLimitException(String message) {
        super(message);
    }
}
