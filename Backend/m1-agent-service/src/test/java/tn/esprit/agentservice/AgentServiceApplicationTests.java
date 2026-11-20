package tn.esprit.agentservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.dto.AgentRequest;
import tn.esprit.agentservice.repository.OutboxEventRepository;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;
import tn.esprit.agentservice.service.AgentService;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AgentServiceApplicationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentVersionRepository versionRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private AgentExecutionRepository executionRepository;

    @Autowired
    private AgentService agentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiDocumentListsAgentEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("M1 - Agent Management API"))
                .andExpect(jsonPath("$.paths['/api/agents']").exists());
    }

    @Test
    @Transactional
    @Rollback
    void agentListIsAUserScopedJsonArray() throws Exception {
        Agent ownAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("Owned by dev user")
                .ownerId("dev-user")
                .status(AgentStatus.DRAFT)
                .build());
        Agent otherAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("Owned by another user")
                .ownerId("another-user")
                .status(AgentStatus.DRAFT)
                .build());

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + ownAgent.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + otherAgent.getId() + ")]").doesNotExist());
    }

    @Test
    @Transactional
    @Rollback
    void getOfAnotherUsersAgentReturnsNotFound() throws Exception {
        Agent otherAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("Private agent")
                .ownerId("another-user")
                .status(AgentStatus.DRAFT)
                .build());

        mockMvc.perform(get("/api/agents/{id}", otherAgent.getId()).with(user("caller")))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthIsUpWithoutAnAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"status\":\"UP\"}"));
    }

    @Test
    @Transactional
    @Rollback
    void agentCreationStoresItsEventInTheSameDatabaseTransaction() {
        var response = agentService.create(new AgentRequest("Outbox integration", null, AgentStatus.DRAFT), "dev-user");
        var event = outboxRepository.findAll().stream()
                .filter(row -> row.getEventType().equals("AGENT_CREATED"))
                .filter(row -> row.getPayload().contains("\"agentId\":" + response.id()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OutboxStatus.PENDING, event.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("agent.created", event.getRoutingKey());
        org.junit.jupiter.api.Assertions.assertTrue(event.getPayload().contains("\"event\":\"AGENT_CREATED\""));
        org.junit.jupiter.api.Assertions.assertTrue(event.getPayload().contains("\"source\":\"agent-service\""));
    }

    @Test
    @Transactional
    @Rollback
    void deletingAgentAlsoDeletesExecutionHistory() throws Exception {
        Agent agent = agentRepository.saveAndFlush(Agent.builder()
                .name("Delete history test")
                .ownerId("dev-user")
                .status(AgentStatus.DRAFT)
                .build());
        AgentExecution execution = new AgentExecution();
        execution.setAgentId(agent.getId());
        execution.setVersionId(99L);
        execution.setVersionNumber(1);
        execution.setRequestedBy("dev-user");
        execution.setInput("test");
        execution.setAppliedSettings("{}");
        execution.setStatus(ExecutionStatus.FAILED);
        executionRepository.saveAndFlush(execution);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/agents/{id}", agent.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        org.junit.jupiter.api.Assertions.assertTrue(executionRepository.findByAgentId(agent.getId()).isEmpty());
    }

    @Test
    @Transactional
    @Rollback
    void createsM5VersionFromOwnedVersionOverHttp() throws Exception {
        Agent agent = agentRepository.saveAndFlush(Agent.builder()
                .name("M5 test agent")
                .ownerId("dev-user")
                .status(AgentStatus.DRAFT)
                .build());
        AgentConfiguration configuration = AgentConfiguration.builder()
                .temperature(0.7)
                .maxTokens(512)
                .style("concise")
                .maxTurns(3)
                .timeoutSeconds(30)
                .allowedToolIds(List.of(15L, 19L))
                .build();
        AgentVersion baseVersion = versionRepository.saveAndFlush(AgentVersion.builder()
                .agent(agent)
                .versionNumber(2)
                .model("gpt-4o-mini")
                .systemPrompt("Base prompt")
                .active(true)
                .configuration(configuration)
                .build());
        String payload = """
                {
                  "basedOnVersionId": %d,
                  "source": "M5",
                  "studyId": 4,
                  "params": {
                    "temperature": 0.3,
                    "maxTokens": 1024,
                    "style": "detaille"
                  }
                }
                """.formatted(baseVersion.getId());

        mockMvc.perform(post("/api/agents/{id}/versions", agent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.source").value("M5"))
                .andExpect(jsonPath("$.studyId").value(4))
                .andExpect(jsonPath("$.label").value("Version 3"))
                .andExpect(jsonPath("$.configuration.maxTokens").value(1024))
                .andExpect(jsonPath("$.configuration.style").value("detaille"))
                .andExpect(jsonPath("$.configuration.allowedToolIds[0]").value(15))
                .andExpect(jsonPath("$.configuration.allowedToolIds[1]").value(19));

        AgentVersion createdVersion = versionRepository.findByAgentId(agent.getId()).stream()
                .filter(version -> version.getVersionNumber() == 3)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/agents/{id}/versions", agent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.label == 'Version 2 (active)')]").exists())
                .andExpect(jsonPath("$[?(@.label == 'Version 3')]").exists());
        mockMvc.perform(get(
                        "/api/agents/{id}/versions/{vid}/tunable-parameters",
                        agent.getId(),
                        baseVersion.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[1].name").value("maxTokens"))
                .andExpect(jsonPath("$[1].current").value(512))
                .andExpect(jsonPath("$[4].choices[0]").value("concise"))
                .andExpect(jsonPath("$[4].min").doesNotExist());
        mockMvc.perform(put(
                        "/api/agents/{id}/versions/{vid}/activate",
                        agent.getId(),
                        createdVersion.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        org.junit.jupiter.api.Assertions.assertFalse(
                versionRepository.findByIdAndAgentId(baseVersion.getId(), agent.getId()).orElseThrow().isActive()
        );
    }

    @Test
    @Transactional
    @Rollback
    void acceptsExistingUiVersionPayloadOverHttp() throws Exception {
        Agent agent = agentRepository.saveAndFlush(Agent.builder()
                .name("UI test agent")
                .ownerId("dev-user")
                .status(AgentStatus.DRAFT)
                .build());

        mockMvc.perform(post("/api/agents/{id}/versions", agent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o-mini",
                                  "systemPrompt": "Answer helpfully",
                                  "changeLog": "Created in UI",
                                  "configuration": {
                                    "temperature": 0.7,
                                    "maxTurns": 3,
                                    "timeoutSeconds": 30,
                                    "allowedToolIds": []
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.source").value("USER"))
                .andExpect(jsonPath("$.configuration.maxTokens").doesNotExist());
    }

}
