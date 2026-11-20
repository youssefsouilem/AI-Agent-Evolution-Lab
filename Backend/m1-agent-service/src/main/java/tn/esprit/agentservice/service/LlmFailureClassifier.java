package tn.esprit.agentservice.service;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@Component
public class LlmFailureClassifier {
    public Failure classify(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            int status = status(cause);
            if (status == 401 || status == 403) {
                return new Failure("PROVIDER_AUTH_FAILED", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle a refusé l'authentification.");
            }
            if (status == 404) {
                return new Failure("MODEL_NOT_AVAILABLE", HttpStatus.BAD_GATEWAY,
                        "Le modèle demandé n'est pas disponible.");
            }
            if (status == 429) {
                return new Failure("PROVIDER_RATE_LIMITED", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle limite les requêtes.");
            }
            if (status >= 500) {
                return new Failure("PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle est indisponible.");
            }
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException
                    || cause.getClass().getSimpleName().contains("Timeout")) {
                return new Failure("TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                        "Le délai d'exécution du modèle est dépassé.");
            }
            if (cause instanceof ConnectException || cause instanceof IOException) {
                return new Failure("PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle est indisponible.");
            }
        }
        return new Failure("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue pendant l'exécution.");
    }

    private int status(Throwable cause) {
        if (cause instanceof HttpStatusCodeException exception) {
            return exception.getStatusCode().value();
        }
        if (cause instanceof FeignException exception) {
            return exception.status();
        }
        return -1;
    }

    public record Failure(String code, HttpStatus status, String message) { }
}
