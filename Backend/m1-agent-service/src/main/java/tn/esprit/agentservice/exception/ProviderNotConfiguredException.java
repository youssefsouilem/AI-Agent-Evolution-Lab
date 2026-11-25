package tn.esprit.agentservice.exception;

public class ProviderNotConfiguredException extends RuntimeException {
    public ProviderNotConfiguredException() {
        super("Aucun fournisseur de modèle n'est configuré.");
    }
}
