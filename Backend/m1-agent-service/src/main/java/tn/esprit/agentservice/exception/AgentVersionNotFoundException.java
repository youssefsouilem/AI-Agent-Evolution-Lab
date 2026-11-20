package tn.esprit.agentservice.exception;

public class AgentVersionNotFoundException extends ResourceNotFoundException {

    public AgentVersionNotFoundException(Long versionId, Long agentId) {
        super("La version demandée est introuvable pour cet agent.");
    }
}
