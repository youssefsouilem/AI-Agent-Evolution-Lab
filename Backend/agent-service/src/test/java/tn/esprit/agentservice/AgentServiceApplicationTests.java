package tn.esprit.agentservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
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
                .andExpect(jsonPath("$.info.title").value("Agent Service API"))
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

        mockMvc.perform(get("/api/agents/{id}", otherAgent.getId()))
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
