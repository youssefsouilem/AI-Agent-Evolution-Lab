package tn.esprit.agentservice.exception;

public class RateLimitedException extends RuntimeException {
    public RateLimitedException() {
        super("La limite d'utilisation de l'assistant est atteinte.");
    }
}
