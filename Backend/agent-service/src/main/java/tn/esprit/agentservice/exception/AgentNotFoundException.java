package tn.esprit.agentservice.exception;

public class AgentNotFoundException extends ResourceNotFoundException {

    public AgentNotFoundException(Long id) {
        super("Agent " + id + " was not found.");
    }
}
