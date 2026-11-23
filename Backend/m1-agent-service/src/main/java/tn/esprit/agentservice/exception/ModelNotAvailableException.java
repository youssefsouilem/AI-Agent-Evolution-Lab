package tn.esprit.agentservice.exception;

public class ModelNotAvailableException extends RuntimeException {
    public ModelNotAvailableException() {
        super("Le modèle demandé n'est pas disponible dans le catalogue.");
    }
}
