package tn.esprit.agentservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:model_validation_api;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "m1.outbox.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "OPENAI_API_KEY=",
        "GEMINI_API_KEY=",
        "GROQ_API_KEY=",
        "OPENROUTER_API_KEY=",
        "m1.llm.strict-model-check=true"
})
class ModelValidationApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AgentRepository agents;
    @Autowired private AgentVersionRepository versions;
    @Autowired private AgentExecutionRepository executions;

    @Test
    void catalogueReportsMissingKeyWithoutRevealingIt() throws Exception {
        mockMvc.perform(get("/api/llm/models")
                        .header("X-Dev-User", "catalog-user").header("X-Dev-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("openai"))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].reason")
                        .value("Variable OPENAI_API_KEY absente ou vide dans le processus de M1"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("missing-key"))));
    }

    @Test
    void versionCreationRejectsModelWithoutAnAvailableProvider() throws Exception {
        Agent agent = agents.saveAndFlush(Agent.builder().name("strict model check")
                .ownerId("model-owner").status(AgentStatus.DRAFT).build());

        mockMvc.perform(post("/api/agents/{id}/versions", agent.getId())
                        .header("X-Dev-User", "model-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o-mini",
                                  "systemPrompt": "Answer briefly",
                                  "configuration": {
                                    "temperature": 0.4,
                                    "maxTokens": 512,
                                    "maxTurns": 3,
                                    "timeoutSeconds": 30,
                                    "style": "concise",
                                    "allowedToolIds": []
                                  }
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_AVAILABLE"));
    }

    @Test
    void executionRejectsUnavailableModelBeforeCreatingHistory() throws Exception {
        Agent agent = agents.saveAndFlush(Agent.builder().name("execute strict model check")
                .ownerId("execute-model-owner").status(AgentStatus.ACTIVE).build());
        AgentVersion version = versions.saveAndFlush(AgentVersion.builder()
                .agent(agent).versionNumber(1).model("not-in-catalogue").systemPrompt("test").active(true)
                .configuration(AgentConfiguration.builder().temperature(0.7).maxTokens(512)
                        .maxTurns(3).timeoutSeconds(30).style("concise").allowedToolIds(java.util.List.of()).build())
                .build());

        mockMvc.perform(post("/api/agents/{id}/execute", agent.getId())
                        .header("X-Dev-User", "execute-model-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + version.getId() + ",\"input\":\"test\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_AVAILABLE"));
        org.junit.jupiter.api.Assertions.assertEquals(0, executions.countByAgentId(agent.getId()));
    }

    @Test
    void assistantReportsUnconfiguredProviderAs502() throws Exception {
        mockMvc.perform(post("/api/agents/assist")
                        .header("X-Dev-User", "no-provider-user").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Résumé\",\"language\":\"fr\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_NOT_CONFIGURED"));
    }
}
