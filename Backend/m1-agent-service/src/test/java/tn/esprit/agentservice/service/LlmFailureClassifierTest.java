package tn.esprit.agentservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LlmFailureClassifierTest {
    private final LlmFailureClassifier classifier = new LlmFailureClassifier();

    @Test
    void mapsProviderHttpFailuresWithoutExposingProviderText() {
        assertFailure(HttpStatus.UNAUTHORIZED, "PROVIDER_AUTH_FAILED");
        assertFailure(HttpStatus.FORBIDDEN, "PROVIDER_AUTH_FAILED");
        assertFailure(HttpStatus.NOT_FOUND, "MODEL_NOT_AVAILABLE");
        assertFailure(HttpStatus.TOO_MANY_REQUESTS, "PROVIDER_RATE_LIMITED");
        assertEquals("PROVIDER_UNAVAILABLE", classifier.classify(
                new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "sk-test-secret")).code());
    }

    @Test
    void mapsConnectionAndTimeoutFailures() throws Exception {
        assertEquals("PROVIDER_UNAVAILABLE", classifier.classify(new ConnectException("secret")).code());
        assertEquals("TIMEOUT", classifier.classify(new SocketTimeoutException("Bearer secret")).code());
        assertFalse(classifier.classify(new ConnectException("sk-sensitive")).message().contains("sk-sensitive"));
    }

    private void assertFailure(HttpStatus status, String code) {
        var failure = classifier.classify(new HttpClientErrorException(status, "raw provider body"));
        assertEquals(code, failure.code());
        assertEquals(HttpStatus.BAD_GATEWAY, failure.status());
        assertFalse(failure.message().contains("raw provider body"));
    }
}
