package tn.esprit.agentservice.client;

public class ToolHttpException extends RuntimeException {
    private final int status;
    public ToolHttpException(int status) {
        super("Tool service rejected the request.");
        this.status = status;
    }
    public int getStatus() { return status; }
}
