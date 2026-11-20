package tn.esprit.agentservice.exception;

public class AgentAccessDeniedException extends ForbiddenOperationException {

    public AgentAccessDeniedException() {
        super("Only the agent owner or an admin may perform this operation.");
    }
}
