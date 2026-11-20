package tn.esprit.agentservice.exception;

public class DownstreamTimeoutException extends RuntimeException {

    public DownstreamTimeoutException(String message) {
        super(message);
    }

    public DownstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
