package tn.esprit.agentservice.exception;

public class ModelAmbiguousException extends RuntimeException {
    public ModelAmbiguousException() {
        super("Ce modèle est proposé par plusieurs fournisseurs. Précisez le fournisseur.");
    }
}
