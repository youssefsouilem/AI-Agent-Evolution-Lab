package tn.esprit.agentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Order;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.ExecuteRequest;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.exception.ExecutionFailedException;
import tn.esprit.agentservice.exception.ResourceNotFoundException;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.MvcResult;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution_history_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "m1.demo.seed=false",
        "m1.outbox.poll-interval-ms=3600000",
        "OPENAI_API_KEY=test-placeholder"
})
@Import(AgentExecutionHistoryTest.GatewayTestConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentExecutionHistoryTest {

    private static final AtomicInteger listRequests = new AtomicInteger();
    private static final AtomicInteger executeRequests = new AtomicInteger();
    private static final AtomicInteger executeStatus = new AtomicInteger(200);
    private static final AtomicInteger sleepMillis = new AtomicInteger();
    private static final AtomicReference<String> executeBody = new AtomicReference<>();
    private static final String FAKE_M2_URL;
    private static final HttpServer FAKE_M2;

    static {
        try {
            FAKE_M2 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FAKE_M2.createContext("/api/tools", AgentExecutionHistoryTest::handleFakeM2);
            FAKE_M2.start();
            FAKE_M2_URL = "http://127.0.0.1:" + FAKE_M2.getAddress().getPort();
        } catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void fakeM2Properties(DynamicPropertyRegistry registry) {
        registry.add("tools.service.url", () -> FAKE_M2_URL);
    }

    @Autowired private AgentExecutionService executionService;
    @Autowired private FakeLlmGateway gateway;
    @Autowired private AgentRepository agents;
    @Autowired private AgentVersionRepository versions;
    @Autowired private AgentExecutionRepository executions;
    @Autowired private AgentExecutionStore executionStore;
    @Autowired private AgentService agentService;
    @Autowired private AgentExecutionHistoryService historyService;
    @Autowired private OutboxEventRepository outboxEvents;
    @Autowired private WebApplicationContext applicationContext;

    private AgentVersion version;

    @BeforeEach
    void setUp() {
        executions.deleteAll();
        versions.deleteAll();
        agents.deleteAll();
        Agent agent = agents.saveAndFlush(Agent.builder().name("Execution test").ownerId("dev-user")
                .status(AgentStatus.ACTIVE).build());
        version = versions.saveAndFlush(AgentVersion.builder().agent(agent).versionNumber(1).model("gpt-4o-mini")
                .systemPrompt("Always answer in French.")
                .active(true)
                .configuration(AgentConfiguration.builder().temperature(0.7).maxTurns(3).timeoutSeconds(30)
                        .allowedToolIds(List.of()).build())
                .build());
        gateway.result.set(new LlmResult("Terminé", 2, 3, 5));
        gateway.callTool.set(false);
        gateway.toolCallsToMake.set(1);
        executeStatus.set(200);
        sleepMillis.set(0);
    }

    @Test
    @Order(1)
    void overridesAreStoredForTheExecutionAndNeverMutateTheVersion() throws Exception {
        var overrides = Map.<String, Object>of("temperature", 0.1);
        var response = executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "Analyse.",
                        "mission=5; étape=2", overrides),
                new CurrentUser("dev-user", false));

        assertEquals(5, response.tokensUsed());
        assertTrue(gateway.lastRequest.get().userMessage().contains("Context:\nmission=5; étape=2"));
        AgentVersion unchanged = versions.findById(version.getId()).orElseThrow();
        assertEquals(0.7, unchanged.getConfiguration().getTemperature());
        var execution = executions.findById(response.executionId()).orElseThrow();
        assertEquals("{\"temperature\":0.1}", execution.getOverrides());
        var settings = new ObjectMapper().readTree(execution.getAppliedSettings());
        assertEquals(0.1, settings.get("temperature").asDouble());
        assertEquals(ExecutionStatus.COMPLETED, execution.getStatus());
    }

    @Test
    @Order(2)
    void blankLlmAnswerReturnsStructuredFailureWithUnknownMetrics() throws Exception {
        gateway.result.set(new LlmResult("  ", null, null, null));
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/agents/{id}/execute", version.getAgent().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"versionId":%d,"input":"Analyse."}
                                """.formatted(version.getId())))
                .andExpect(status().isBadGateway())
                .andReturn();
        var error = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertEquals("EMPTY_RESPONSE", error.get("errorCode").asText());
        assertTrue(error.get("execution").get("executionId").isNumber());
        org.junit.jupiter.api.Assertions.assertTrue(error.get("execution").get("promptTokens").isNull());
        var execution = executions.findById(error.get("execution").get("executionId").asLong()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertEquals("EMPTY_RESPONSE", execution.getErrorCode());
        assertNull(execution.getTotalTokens());
    }

    @Test
    @Order(3)
    void providerUnknownTokenUsageRemainsNullOnSuccess() {
        gateway.result.set(new LlmResult("Réponse", null, null, null));
        var response = executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "Analyse.", null, null),
                new CurrentUser("dev-user", false));
        assertNull(response.tokensUsed());
        assertNull(response.promptTokens());
        assertNull(response.completionTokens());
        assertNull(executions.findById(response.executionId()).orElseThrow().getTotalTokens());
    }

    @Test
    @Order(4)
    void acceptsJsonObjectContext() throws Exception {
        executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "Analyse.",
                        new ObjectMapper().readTree("{\"mission\":5}"), null),
                new CurrentUser("dev-user", false));
        assertTrue(gateway.lastRequest.get().userMessage().contains("Context:\n{\"mission\":5}"));
    }

    @Test
    @Order(5)
    void onlyAllowedToolsAreOfferedAndEmptyAllowlistSkipsM2() throws Exception {
        setAllowedTools(List.of(55L));
        gateway.callTool.set(true);
        int beforeList = listRequests.get();
        executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "Use a tool.", null, null), new CurrentUser("dev-user", false));
        assertEquals(1, gateway.callbackCount.get());
        assertEquals(beforeList + 1, listRequests.get());
        assertEquals(1, executeRequests.get());
        var sentToolRequest = new ObjectMapper().readTree(executeBody.get());
        assertEquals(version.getId(), sentToolRequest.get("agentVersionId").asLong());
        assertTrue(sentToolRequest.get("arguments").isObject());

        setAllowedTools(List.of());
        int beforeEmptyList = listRequests.get();
        executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "No tools.", null, null), new CurrentUser("dev-user", false));
        assertEquals(beforeEmptyList, listRequests.get());
    }

    @Test
    @Order(6)
    void historyEndpointsReturnArraysAndTotalCount() throws Exception {
        var response = executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "History request.", null, null),
                new CurrentUser("dev-user", false));
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        mockMvc.perform(get("/api/agents/{id}/executions", version.getAgent().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(get("/api/agents/{id}/executions/{executionId}",
                        version.getAgent().getId(), response.executionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input").value("History request."))
                .andExpect(jsonPath("$.output").value("Terminé"));
        mockMvc.perform(get("/api/executions").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    @Order(7)
    void m2ForbiddenToolIsReportedToLlmAsDenied() throws Exception {
        setAllowedTools(List.of(55L));
        gateway.callTool.set(true);
        executeStatus.set(403);
        var response = executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "Use a tool.", null, null), new CurrentUser("dev-user", false));
        var execution = executions.findById(response.executionId()).orElseThrow();
        assertEquals(1, response.toolCallCount());
        assertEquals("DENIED", new ObjectMapper().readTree(execution.getToolCalls()).get(0).get("status").asText());
        assertEquals("{\"error\":\"Tool not authorized\"}", gateway.toolResult.get());
    }

    @Test
    @Order(8)
    void m2InvalidArgumentsAreReportedToLlm() {
        setAllowedTools(List.of(55L));
        gateway.callTool.set(true);
        executeStatus.set(422);
        executionService.execute(version.getAgent().getId(),
                new ExecuteRequest(version.getId(), "Use a tool.", null, null), new CurrentUser("dev-user", false));
        assertEquals("{\"error\":\"Invalid tool arguments\"}", gateway.toolResult.get());
    }

    @Test
    @Order(9)
    void maxTurnsAllowsNCallsAndRejectsCallNPlusOne() {
        setAllowedTools(List.of(55L));
        setMaxTurns(1);
        gateway.callTool.set(true);
        gateway.toolCallsToMake.set(2);
        int before = executeRequests.get();
        ExecutionFailedException failure = assertThrows(ExecutionFailedException.class, () ->
                executionService.execute(version.getAgent().getId(),
                        new ExecuteRequest(version.getId(), "Use a tool.", null, null),
                        new CurrentUser("dev-user", false)));
        assertEquals("TOOL_LIMIT_EXCEEDED", failure.getErrorCode());
        assertEquals(2, failure.getToolCallCount());
        assertEquals(before + 1, executeRequests.get());
    }

    @Test
    @Order(10)
    void m2ServerFailureBecomesStructured502() {
        setAllowedTools(List.of(55L));
        gateway.callTool.set(true);
        executeStatus.set(503);
        ExecutionFailedException failure = assertThrows(ExecutionFailedException.class, () ->
                executionService.execute(version.getAgent().getId(),
                        new ExecuteRequest(version.getId(), "Use a tool.", null, null),
                        new CurrentUser("dev-user", false)));
        assertEquals("TOOL_SERVICE_UNAVAILABLE", failure.getErrorCode());
    }

    @Test
    @Order(11)
    void slowM2CallBecomesStructured504() {
        setAllowedTools(List.of(55L));
        gateway.callTool.set(true);
        sleepMillis.set(4000);
        ExecutionFailedException failure = assertThrows(ExecutionFailedException.class, () ->
                executionService.execute(version.getAgent().getId(),
                        new ExecuteRequest(version.getId(), "Use a tool.", null, null),
                        new CurrentUser("dev-user", false)));
        assertEquals("TOOL_TIMEOUT", failure.getErrorCode());
    }

    @Test
    @Order(12)
    void unavailableM2ConnectionBecomesStructured502() {
        setAllowedTools(List.of(55L));
        gateway.callTool.set(true);
        FAKE_M2.stop(0);
        ExecutionFailedException failure = assertThrows(ExecutionFailedException.class, () ->
                executionService.execute(version.getAgent().getId(),
                        new ExecuteRequest(version.getId(), "Use a tool.", null, null),
                        new CurrentUser("dev-user", false)));
        assertEquals("TOOL_SERVICE_UNAVAILABLE", failure.getErrorCode());
    }

    @Test
    @Order(13)
    void staleRunningExecutionIsFailedAndItsEventIsWritten() {
        AgentExecution stale = new AgentExecution();
        stale.setAgentId(version.getAgent().getId());
        stale.setVersionId(version.getId());
        stale.setVersionNumber(version.getVersionNumber());
        stale.setRequestedBy("dev-user");
        stale.setInput("interrupted");
        stale.setAppliedSettings("{}");
        stale.setStatus(ExecutionStatus.RUNNING);
        stale.setStartedAt(LocalDateTime.now().minusMinutes(10));
        AgentExecution savedStale = executions.saveAndFlush(stale);

        executionStore.failStale(LocalDateTime.now().minusMinutes(5));

        AgentExecution recovered = executions.findById(savedStale.getId()).orElseThrow();
        assertEquals(ExecutionStatus.FAILED, recovered.getStatus());
        assertEquals("SERVICE_RESTART", recovered.getErrorCode());
        assertTrue(recovered.getDurationMs() >= 0);
        assertTrue(outboxEvents.findAll().stream()
                .anyMatch(event -> event.getEventType().equals("AGENT_EXECUTION_FAILED")
                        && event.getPayload().contains("\"executionId\":" + savedStale.getId())));
    }

    @Test
    @Order(14)
    void endToEndExecutionHistoryOwnershipAndAgentDeletion() throws Exception {
        Long agentId = version.getAgent().getId();
        var response = executionService.execute(agentId,
                new ExecuteRequest(version.getId(), "End-to-end task.", null, Map.of("temperature", 0.1)),
                new CurrentUser("dev-user", false));

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        mockMvc.perform(get("/api/agents/{id}/executions", agentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(response.executionId()))
                .andExpect(header().string("X-Total-Count", "1"));
        assertThrows(ResourceNotFoundException.class, () ->
                historyService.detail(agentId, response.executionId(), new CurrentUser("another-user", false)));

        agentService.delete(agentId, new CurrentUser("dev-user", false));
        assertTrue(executions.findByAgentId(agentId).isEmpty());
    }

    private void setAllowedTools(List<Long> toolIds) {
        version.getConfiguration().setAllowedToolIds(toolIds);
        version = versions.saveAndFlush(version);
        gateway.callbackCount.set(0);
        gateway.toolResult.set(null);
    }

    private void setMaxTurns(int maxTurns) {
        version.getConfiguration().setMaxTurns(maxTurns);
        version = versions.saveAndFlush(version);
    }

    private static void handleFakeM2(HttpExchange exchange) throws java.io.IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            listRequests.incrementAndGet();
            send(exchange, 200, """
                    [{"id":55,"name":"allowed_tool","description":"Allowed","inputSchema":{"type":"object"}},
                     {"id":56,"name":"not_allowed_tool","description":"Not allowed","inputSchema":{"type":"object"}}]
                    """);
            return;
        }
        executeRequests.incrementAndGet();
        executeBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        int delay = sleepMillis.get();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }
        if (executeStatus.get() != 200) {
            send(exchange, executeStatus.get(), "{\"error\":\"redacted\"}");
        } else {
            send(exchange, 200, "{\"status\":\"OK\",\"output\":\"M2 result\"}");
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @TestConfiguration
    static class GatewayTestConfiguration {
        @Bean
        @Primary
        FakeLlmGateway fakeLlmGateway() {
            return new FakeLlmGateway();
        }
    }

    static class FakeLlmGateway implements LlmGateway {
        private final AtomicReference<LlmResult> result =
                new AtomicReference<>(new LlmResult("Terminé", 2, 3, 5));
        private final AtomicBoolean callTool = new AtomicBoolean();
        private final AtomicInteger callbackCount = new AtomicInteger();
        private final AtomicInteger toolCallsToMake = new AtomicInteger(1);
        private final AtomicReference<String> toolResult = new AtomicReference<>();
        private final AtomicReference<LlmRequest> lastRequest = new AtomicReference<>();

        @Override
        public LlmResult call(LlmRequest request) {
            lastRequest.set(request);
            callbackCount.set(request.toolCallbacks().size());
            if (callTool.get() && !request.toolCallbacks().isEmpty()) {
                ToolCallback callback = request.toolCallbacks().get(0);
                for (int i = 0; i < toolCallsToMake.get(); i++) {
                    toolResult.set(callback.call("{}"));
                }
            }
            return result.get();
        }
    }
}
