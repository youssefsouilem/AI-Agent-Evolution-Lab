package tn.esprit.agentservice.exception;

public class AgentVersionNotFoundException extends ResourceNotFoundException {

    public AgentVersionNotFoundException(Long versionId, Long agentId) {
        super("Version " + versionId + " was not found for agent " + agentId + ".");
    }
}
