package tn.esprit.agentservice.exception;

public class ModelNotAvailableException extends RuntimeException {
    public ModelNotAvailableException() {
        super("Le modèle demandé n'est pas disponible dans le catalogue.");
    }

    public ModelNotAvailableException(String modelId) {
        super("Le modèle \"" + modelId
                + "\" n'est pas disponible. Vérifiez la liste actuelle des modèles du fournisseur.");
    }
}
