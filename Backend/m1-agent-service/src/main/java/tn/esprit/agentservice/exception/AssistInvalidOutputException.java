package tn.esprit.agentservice.exception;

public class AssistInvalidOutputException extends RuntimeException {
    public AssistInvalidOutputException() {
        super("Le fournisseur n'a pas retourné une proposition valide.");
    }
}
