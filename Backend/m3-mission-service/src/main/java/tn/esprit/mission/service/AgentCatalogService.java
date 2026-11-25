package tn.esprit.mission.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import tn.esprit.mission.dto.AgentResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class AgentCatalogService {

    private final ObjectMapper objectMapper;

    private final M1AgentClient m1Client;
    private final boolean m1Enabled;

    public AgentCatalogService(ObjectMapper objectMapper, M1AgentClient m1Client,
        @Value("${m1.enabled:false}") boolean m1Enabled) {
        this.objectMapper = objectMapper;
        this.m1Client = m1Client;
        this.m1Enabled = m1Enabled;
    }

    public List<AgentResponse> getAgents() {
        if (m1Enabled) return m1Client.getAgents();
        ClassPathResource resource =
                new ClassPathResource("mocks/agents.json");

        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(
                    input,
                    new TypeReference<List<AgentResponse>>() {}
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible de lire le catalogue simulé des agents.",
                    exception
            );
        }
    }
}