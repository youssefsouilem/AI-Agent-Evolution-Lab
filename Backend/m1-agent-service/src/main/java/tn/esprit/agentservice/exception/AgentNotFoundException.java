package tn.esprit.agentservice.exception;

public class AgentNotFoundException extends ResourceNotFoundException {

    public AgentNotFoundException(Long id) {
        super("L'agent demandé est introuvable.");
    }
}
