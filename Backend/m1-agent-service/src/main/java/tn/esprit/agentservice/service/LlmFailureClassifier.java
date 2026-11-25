package tn.esprit.agentservice.service;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.stereotype.Component;
import tn.esprit.agentservice.exception.ProviderNotConfiguredException;
import tn.esprit.agentservice.exception.ModelNotAvailableException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@Component
public class LlmFailureClassifier {
    public Failure classify(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ProviderNotConfiguredException) {
                return new Failure("PROVIDER_NOT_CONFIGURED", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle n'est pas configuré.");
            }
            if (cause instanceof ModelNotAvailableException exception) {
                return new Failure("MODEL_NOT_AVAILABLE", HttpStatus.UNPROCESSABLE_ENTITY,
                        exception.getMessage());
            }
            int status = status(cause);
            if (status == 401 || status == 403) {
                return new Failure("PROVIDER_AUTH_FAILED", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle a refusé l'authentification. "
                                + "Vérifiez la configuration de sa clé API.");
            }
            if (status == 404) {
                return new Failure("MODEL_NOT_AVAILABLE", HttpStatus.BAD_GATEWAY,
                        "Le modèle demandé n'est pas disponible.");
            }
            if (status == 429) {
                return new Failure("PROVIDER_RATE_LIMITED", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle limite les requêtes.");
            }
            if (status == 400) {
                return new Failure("PROVIDER_REQUEST_REJECTED", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur a rejeté la requête. Vérifiez le modèle et ses paramètres.");
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
        if (cause instanceof OpenAiCompatibleLlmGateway.ProviderHttpException exception) {
            return exception.statusCode();
        }
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
