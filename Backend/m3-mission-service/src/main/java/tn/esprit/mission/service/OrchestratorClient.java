package tn.esprit.mission.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.esprit.mission.dto.EngineRequest;

@Component
public class OrchestratorClient {
    private final RestClient.Builder builder;
    private final String baseUrl;
    private final boolean enabled;
    private final String token;
    public OrchestratorClient(RestClient.Builder builder,
        @Value("${orchestrator.base-url:http://127.0.0.1:8093}") String baseUrl,
        @Value("${orchestrator.enabled:false}") boolean enabled, @Value("${orchestrator.token:}") String token) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.token = token;
    }
    public boolean enabled() { return enabled; }
    private RestClient client(int timeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(timeoutMs);
        var configured = builder.clone().baseUrl(baseUrl).requestFactory(factory);
        if (!token.isBlank()) configured.defaultHeader("Authorization", "Bearer " + token);
        return configured.build();
    }
    public JsonNode generate(Map<String, Object> request) {
        try { return client(90000).post().uri("/api/orchestrator/generate").body(request).retrieve().body(JsonNode.class); }
        catch (org.springframework.web.client.RestClientException error) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI Router indisponible ou réponse incompatible");
        }
    }
    public JsonNode execute(EngineRequest request) {
        return client((request.maxDuration() + 35) * 1000).post().uri("/api/orchestrator/execute")
            .body(request).retrieve().body(JsonNode.class);
    }
    public JsonNode get(Long executionId) {
        return client(3000).get().uri("/api/orchestrator/executions/{id}", executionId)
            .retrieve().body(JsonNode.class);
    }
    public JsonNode resume(Long executionId, boolean approved, int timeoutSeconds) {
        return client((timeoutSeconds + 35) * 1000).post()
            .uri("/api/orchestrator/executions/{id}/resume", executionId)
            .body(Map.of("approved", approved)).retrieve().body(JsonNode.class);
    }
}
