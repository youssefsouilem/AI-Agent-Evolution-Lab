package tn.esprit.agentservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.repository.AdminAuditRepository;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feature_api;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "m1.outbox.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "OPENAI_API_KEY=test-key-not-real")
class FeatureApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AgentRepository agents;
    @Autowired private AgentExecutionRepository executions;
    @Autowired private AgentVersionRepository versions;
    @Autowired private AdminAuditRepository audits;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void quotaAllowsFiveAndDeleteFreesASlot() throws Exception {
        String owner = "quota-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            create(owner, "USER").andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/me").header("X-Dev-User", owner).header("X-Dev-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.displayName").value(owner))
                .andExpect(jsonPath("$.limits.maxAgents").value(5))
                .andExpect(jsonPath("$.usage.agents").value(5));
        create(owner, "USER").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AGENT_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.usage").value(5));
        Agent oldest = agents.findByOwnerId(owner).get(0);
        mockMvc.perform(delete("/api/agents/{id}", oldest.getId())
                        .header("X-Dev-User", owner).header("X-Dev-Role", "USER"))
                .andExpect(status().isNoContent());
        create(owner, "USER").andExpect(status().isCreated());
        assertEquals(5, agents.countByOwnerId(owner));
    }

    @Test
    void adminsAreUnlimitedAndCanReadTheirEffectiveProfileWithoutALimit() throws Exception {
        String owner = "admin-quota-" + UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            create(owner, "ADMIN").andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/me").header("X-Dev-User", owner).header("X-Dev-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.limits.maxAgents")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.usage.agents").value(6))
                .andExpect(jsonPath("$.capabilities.history").value(true));
    }

    @Test
    void adminQuotaOverrideIsAuditedAndEnforced() throws Exception {
        String owner = "override-" + UUID.randomUUID();
        setQuota(owner, 1);
        create(owner, "USER").andExpect(status().isCreated());
        create(owner, "USER").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.limit").value(1));
        setQuota(owner, 6);
        for (int i = 0; i < 5; i++) {
            create(owner, "USER").andExpect(status().isCreated());
        }
        create(owner, "USER").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.limit").value(6));
        setQuota(owner, null);
        create(owner, "USER").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.limit").value(5));
        assertEquals(3, audits.findAll().stream()
                .filter(row -> owner.equals(row.getTargetOwnerId()))
                .count());
    }

    @Test
    void tenConcurrentCreationsRespectTheDatabaseQuotaLock() throws Exception {
        String owner = "parallel-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> create(owner, "USER").andReturn().getResponse().getStatus()));
            }
            long successes = 0;
            long quotaFailures = 0;
            for (Future<Integer> result : results) {
                int statusCode = result.get();
                if (statusCode == 201) successes++;
                if (statusCode == 403) quotaFailures++;
            }
            assertEquals(5, successes);
            assertEquals(5, quotaFailures);
            assertEquals(5, agents.countByOwnerId(owner));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void adminDeleteRequiresReasonAndCascadesAuditAndEvent() throws Exception {
        Agent agent = agents.saveAndFlush(Agent.builder().name("delete target")
                .ownerId("delete-owner-" + UUID.randomUUID()).status(AgentStatus.DRAFT).build());
        AgentVersion version = versions.saveAndFlush(AgentVersion.builder()
                .agent(agent).versionNumber(1).model("gpt-4o-mini").systemPrompt("test")
                .configuration(AgentConfiguration.builder().temperature(0.7).maxTokens(512)
                        .maxTurns(3).timeoutSeconds(30).style("concise").allowedToolIds(List.of()).build())
                .build());
        AgentExecution execution = new AgentExecution();
        execution.setAgentId(agent.getId());
        execution.setVersionId(version.getId());
        execution.setVersionNumber(1);
        execution.setRequestedBy(agent.getOwnerId());
        execution.setInput("delete test");
        execution.setAppliedSettings("{}");
        execution.setStatus(ExecutionStatus.FAILED);
        executions.saveAndFlush(execution);
        mockMvc.perform(delete("/api/admin/agents/{id}", agent.getId())
                        .header("X-Dev-User", "moderator").header("X-Dev-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/admin/agents/{id}?reason=duplicate", agent.getId())
                        .header("X-Dev-User", "moderator").header("X-Dev-Role", "ADMIN"))
                .andExpect(status().isNoContent());
        assertFalse(agents.existsById(agent.getId()));
        assertTrue(versions.findByAgentId(agent.getId()).isEmpty());
        assertTrue(executions.findByAgentId(agent.getId()).isEmpty());
        assertEquals("duplicate", audits.findAll().stream()
                .filter(row -> agent.getId().equals(row.getTargetAgentId()))
                .findFirst().orElseThrow().getReason());
        assertTrue(outbox.findAll().stream().anyMatch(event ->
                "AGENT_DELETED".equals(event.getEventType())
                        && event.getPayload().contains("\"actorId\":\"moderator\"")
                        && event.getPayload().contains("\"reason\":\"duplicate\"")));
    }

    @Test
    void adminUsersSummaryIncludesOwnerQuotaAndActivity() throws Exception {
        String owner = "admin-summary-" + UUID.randomUUID();
        agents.saveAndFlush(Agent.builder().name("summary").ownerId(owner)
                .status(AgentStatus.ACTIVE).build());
        String response = mockMvc.perform(get("/api/admin/users").header("X-Dev-User", "moderator")
                        .header("X-Dev-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode ownerRow = StreamSupport.stream(objectMapper.readTree(response).spliterator(), false)
                .filter(row -> owner.equals(row.path("ownerId").asText()))
                .findFirst().orElseThrow();
        assertEquals(1, ownerRow.path("agentCount").asInt());
        assertEquals(1, ownerRow.path("activeAgentCount").asInt());
        assertEquals(5, ownerRow.path("effectiveQuota").asInt());
        assertFalse(ownerRow.path("quotaIsOverride").asBoolean());
        assertTrue(ownerRow.path("lastActivityAt").isTextual());
    }

    private void setQuota(String owner, Integer maxAgents) throws Exception {
        String json = maxAgents == null ? "{\"maxAgents\":null}" : "{\"maxAgents\":" + maxAgents + "}";
        mockMvc.perform(put("/api/admin/users/{ownerId}/quota", owner)
                        .header("X-Dev-User", "moderator").header("X-Dev-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void adminArchiveRequiresReasonWritesAuditAndPublishesReason() throws Exception {
        Agent agent = agents.saveAndFlush(Agent.builder().name("moderation target")
                .ownerId("moderated-" + UUID.randomUUID()).status(AgentStatus.ACTIVE).build());
        mockMvc.perform(post("/api/admin/agents/{id}/archive", agent.getId())
                        .header("X-Dev-User", "regular").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/agents/{id}/archive", agent.getId())
                        .header("X-Dev-User", "moderator").header("X-Dev-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/agents/{id}/archive", agent.getId())
                        .header("X-Dev-User", "moderator").header("X-Dev-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"policy review\"}"))
                .andExpect(status().isNoContent());
        assertEquals(AgentStatus.ARCHIVED, agents.findById(agent.getId()).orElseThrow().getStatus());
        assertEquals("policy review", audits.findAll().stream()
                .filter(row -> agent.getId().equals(row.getTargetAgentId()))
                .findFirst().orElseThrow().getReason());
        assertTrue(outbox.findAll().stream().anyMatch(event ->
                "AGENT_UPDATED".equals(event.getEventType())
                        && event.getPayload().contains("\"actorId\":\"moderator\"")
                        && event.getPayload().contains("\"reason\":\"policy review\"")));
    }

    @Test
    void statsKeepUnknownTokensUnknown() throws Exception {
        String owner = "stats-" + UUID.randomUUID();
        Agent agent = agents.saveAndFlush(Agent.builder().name("stats target").ownerId(owner)
                .status(AgentStatus.ACTIVE).build());
        saveExecution(agent.getId(), ExecutionStatus.COMPLETED, 15, 15);
        saveExecution(agent.getId(), ExecutionStatus.FAILED, null, 25);

        mockMvc.perform(get("/api/agents/{id}/stats?days=7", agent.getId())
                        .header("X-Dev-User", owner).header("X-Dev-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.totalTokensKnown").value(15))
                .andExpect(jsonPath("$.executionsWithUnknownTokens").value(1))
                .andExpect(jsonPath("$.byDay[0].tokens").value(15));
    }

    @Test
    void usersCannotUseAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("X-Dev-User", "regular").header("X-Dev-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankAndWhitespaceOnlyInputsAreRejectedOnEveryRequiredTextRoute() throws Exception {
        Agent agent = agents.saveAndFlush(Agent.builder().name("validation target")
                .ownerId("validation-owner").status(AgentStatus.DRAFT).build());
        for (String value : List.of("", "   ")) {
            mockMvc.perform(post("/api/agents/{id}/execute", agent.getId())
                            .header("X-Dev-User", "validation-owner").header("X-Dev-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "input", value))))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(post("/api/admin/agents/{id}/archive", agent.getId())
                            .header("X-Dev-User", "moderator").header("X-Dev-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("reason", value))))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(delete("/api/admin/agents/{id}", agent.getId())
                            .header("X-Dev-User", "moderator").header("X-Dev-Role", "ADMIN")
                            .param("reason", value))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(post("/api/agents/assist")
                            .header("X-Dev-User", "validation-owner").header("X-Dev-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "description", value, "language", "fr"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void timeoutSecondsUsesCatalogueBoundsForWritesWithoutRejectingStoredValues() throws Exception {
        Agent agent = agents.saveAndFlush(Agent.builder().name("timeout target")
                .ownerId("timeout-owner").status(AgentStatus.DRAFT).build());
        AgentVersion storedVersion = versions.saveAndFlush(AgentVersion.builder()
                .agent(agent).versionNumber(1).model("gpt-4o-mini").systemPrompt("stored")
                .configuration(AgentConfiguration.builder().temperature(0.7).maxTokens(512)
                        .maxTurns(3).timeoutSeconds(4).style("concise").allowedToolIds(List.of()).build())
                .build());

        mockMvc.perform(get("/api/agents/{id}/versions/{vid}", agent.getId(), storedVersion.getId())
                        .header("X-Dev-User", "timeout-owner").header("X-Dev-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.timeoutSeconds").value(4));

        for (int timeout : List.of(4, 121)) {
            mockMvc.perform(put("/api/agents/{id}/versions/{vid}/configuration",
                            agent.getId(), storedVersion.getId())
                            .header("X-Dev-User", "timeout-owner").header("X-Dev-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(configurationJson(timeout)))
                    .andExpect(status().isUnprocessableEntity());
        }
        for (int timeout : List.of(5, 120)) {
            mockMvc.perform(put("/api/agents/{id}/versions/{vid}/configuration",
                            agent.getId(), storedVersion.getId())
                            .header("X-Dev-User", "timeout-owner").header("X-Dev-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(configurationJson(timeout)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configuration.timeoutSeconds").value(timeout));
        }
    }

    private String configurationJson(int timeoutSeconds) {
        return """
                {"temperature":0.7,"maxTokens":512,"maxTurns":3,"timeoutSeconds":%d,
                 "style":"concise","allowedToolIds":[]}
                """.formatted(timeoutSeconds);
    }

    private org.springframework.test.web.servlet.ResultActions create(String owner, String role) throws Exception {
        return mockMvc.perform(post("/api/agents")
                .header("X-Dev-User", owner)
                .header("X-Dev-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"quota agent\",\"description\":\"test\"}"));
    }

    private void saveExecution(Long agentId, ExecutionStatus status, Integer tokens, long duration) {
        AgentExecution row = new AgentExecution();
        row.setAgentId(agentId);
        row.setVersionId(1L);
        row.setVersionNumber(1);
        row.setRequestedBy("stats-user");
        row.setInput("test");
        row.setAppliedSettings("{}");
        row.setStatus(status);
        row.setStartedAt(LocalDateTime.now().minusMinutes(1));
        row.setDurationMs(duration);
        row.setTotalTokens(tokens);
        executions.saveAndFlush(row);
    }
}
