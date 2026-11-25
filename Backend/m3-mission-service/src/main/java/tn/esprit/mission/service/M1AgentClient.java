package tn.esprit.mission.service;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tn.esprit.mission.dto.AgentResponse;
import tn.esprit.mission.dto.AgentVersionResponse;
import tn.esprit.mission.exception.AgentCatalogUnavailableException;

@Component
public class M1AgentClient {
    private final RestClient client;

    public M1AgentClient(
        RestClient.Builder builder,
        @Value("${m1.base-url:http://localhost:8081}") String baseUrl,
        @Value("${m1.connect-timeout-ms:2000}") int connectTimeout,
        @Value("${m1.read-timeout-ms:5000}") int readTimeout
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public List<AgentResponse> getAgents() {
        try {
            var agents = client.get().uri("/api/agents").retrieve().body(
                new org.springframework.core.ParameterizedTypeReference<List<AgentResponse>>() {}
            );
            if (agents == null) throw new IllegalArgumentException("Catalogue absent");
            return agents.stream().map(agent -> {
                if (agent.id() == null || agent.name() == null) {
                    throw new IllegalArgumentException("Agent sans identifiant ou nom");
                }
                // M1 may expose versions separately rather than nest them in its catalogue.
                List<AgentVersionResponse> versions = agent.versions();
                if (versions == null) {
                    versions = client.get().uri("/api/agents/{id}/versions", agent.id())
                        .retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<AgentVersionResponse>>() {});
                }
                if (versions == null) throw new IllegalArgumentException("Versions absentes");
                return new AgentResponse(agent.id(), agent.name(), agent.description(), agent.status(), versions);
            }).toList();
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new AgentCatalogUnavailableException(
                "Catalogue M1 inaccessible ou incompatible. Vérifiez M1, son adresse et le contrat JSON.", exception
            );
        }
    }
}
