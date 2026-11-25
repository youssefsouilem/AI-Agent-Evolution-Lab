package tn.esprit.mission.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tn.esprit.mission.dto.M6DiagnosticResponse;
import tn.esprit.mission.dto.MonitoringMetricsRequest;
import tn.esprit.mission.exception.MonitoringContractException;
import tn.esprit.mission.exception.MonitoringUnavailableException;

@Component
public class M6MonitoringClient {

    private final RestClient client;
    private final String detectPath;

    public M6MonitoringClient(
        RestClient.Builder builder,
        @Value("${m6.base-url:http://localhost:8086}") String baseUrl,
        @Value("${m6.detect-path:/api/anomalies/detect}") String detectPath,
        @Value("${m6.connect-timeout-ms:2000}") int connectTimeout,
        @Value("${m6.read-timeout-ms:5000}") int readTimeout
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.detectPath = detectPath;
    }

    public M6DiagnosticResponse detect(MonitoringMetricsRequest metrics) {
        try {
            var diagnostic = client.post()
                .uri(detectPath)
                .body(metrics)
                .retrieve()
                .body(M6DiagnosticResponse.class);
            validate(diagnostic);
            return diagnostic;
        } catch (ResourceAccessException exception) {
            throw new MonitoringUnavailableException(
                "M6 ne répond pas. Aucun diagnostic disponible.", exception
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new MonitoringContractException(
                    "M6 a refusé la requête (HTTP " + exception.getStatusCode().value() +
                    "). Vérifiez l'adresse, les champs et l'authentification.", exception
                );
            }
            throw new MonitoringUnavailableException(
                "M6 est indisponible. Aucun diagnostic disponible.", exception
            );
        } catch (RestClientException exception) {
            // Spring may wrap a read timeout while extracting the response body.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.net.SocketTimeoutException ||
                    cause instanceof java.net.SocketException ||
                    cause instanceof java.io.EOFException) {
                    throw new MonitoringUnavailableException(
                        "La communication avec M6 a été interrompue. Aucun diagnostic disponible.", exception
                    );
                }
            }
            throw new MonitoringContractException(
                "Réponse M6 incompatible avec le contrat attendu.", exception
            );
        }
    }

    public static void validate(M6DiagnosticResponse diagnostic) {
        if (diagnostic == null || diagnostic.anomalyDetected() == null ||
            diagnostic.severity() == null || diagnostic.blocking() == null) {
            throw new MonitoringContractException(
                "Diagnostic M6 incomplet : anomalyDetected, severity et blocking sont obligatoires."
            );
        }
    }
}
