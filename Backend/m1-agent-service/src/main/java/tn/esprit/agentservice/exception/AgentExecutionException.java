package tn.esprit.agentservice.exception;

import org.springframework.http.HttpStatus;
import tn.esprit.agentservice.dto.ExecuteResponse;

public class AgentExecutionException extends RuntimeException {

    private final HttpStatus status;
    private final ExecuteResponse response;

    public AgentExecutionException(HttpStatus status, String message, ExecuteResponse response) {
        super(message);
        this.status = status;
        this.response = response;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ExecuteResponse getResponse() {
        return response;
    }
}
