package tn.esprit.agentservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import java.util.List;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "m1.demo.seed", havingValue = "true")
public class DemoDataSeeder {
    private final AgentRepository agents;
    private final AgentVersionRepository versions;

    public DemoDataSeeder(AgentRepository agents, AgentVersionRepository versions) {
        this.agents = agents;
        this.versions = versions;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        create("Analyste",
                "Tu analyses les données de ventes et listes les anomalies observées.",
                0.2);
        create("Rapporteur",
                "Tu rédiges un rapport clair et structuré à partir des constats de l'Analyste.",
                0.5);
        create("Reviewer",
                "Tu vérifies les contradictions et les preuves manquantes du rapport et réponds APPROUVÉ ou À CORRIGER avec les raisons.",
                0.1);
    }

    private void create(String name, String prompt, double temperature) {
        if (agents.findByOwnerId("dev-user").stream().anyMatch(agent -> name.equals(agent.getName()))) {
            return;
        }
        Agent agent = agents.save(Agent.builder().name(name).ownerId("dev-user")
                .status(AgentStatus.ACTIVE).build());
        AgentConfiguration configuration = AgentConfiguration.builder()
                .temperature(temperature).maxTurns(3).timeoutSeconds(30)
                .allowedToolIds(List.of()).build();
        versions.save(AgentVersion.builder().agent(agent).versionNumber(1).model("gpt-4o-mini")
                .systemPrompt(prompt).source("USER").active(true).configuration(configuration).build());
    }
}
