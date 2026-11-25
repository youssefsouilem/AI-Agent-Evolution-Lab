package tn.esprit.agentservice.security;

import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.slf4j.LoggerFactory;
import tn.esprit.agentservice.entity.AdminAudit;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.repository.AdminAuditRepository;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;
import tn.esprit.agentservice.repository.OutboxEventRepository;
import tn.esprit.agentservice.service.LlmRequest;
import tn.esprit.agentservice.service.LlmSettings;
import tn.esprit.agentservice.service.ProviderRouter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:multi_provider;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "m1.outbox.poll-interval-ms=3600000",
        "m1.llm.strict-model-check=true",
        "m1.llm.check-max-per-hour=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MultiProviderApiTest {
    private static final String SENTINEL = "SENTINEL_TEST_PROVIDER_KEY_7f6a91";
    private static HttpServer fakeProvider;
    private static final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastBody = new AtomicReference<>();
    private static final AtomicInteger responseStatus = new AtomicInteger(200);
    private static final AtomicInteger callCount = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentRepository agents;
    @Autowired private AgentVersionRepository versions;
    @Autowired private AgentExecutionRepository executions;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private AdminAuditRepository audits;
    @Autowired private ProviderRouter router;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        startFakeProvider();
        provider(registry, 0, "openai", "OpenAI", "TEST_OPENAI_API_KEY", "gpt-test", "/openai/v1");
        provider(registry, 1, "gemini", "Google Gemini", "TEST_GEMINI_API_KEY", "gemini-test", "/gemini/v1");
        provider(registry, 2, "groq", "Groq", "TEST_GROQ_API_KEY", "groq-test", "/groq/v1");
        provider(registry, 3, "openrouter", "OpenRouter", "TEST_OPENROUTER_API_KEY",
                "openrouter-test", "/openrouter/v1");
        registry.add("m1.llm.providers[2].models[1].id", () -> "shared-test");
        registry.add("m1.llm.providers[2].models[1].label", () -> "Colliding model");
        registry.add("m1.llm.providers[2].models[1].recommended", () -> "false");
        registry.add("m1.llm.providers[3].models[1].id", () -> "shared-test");
        registry.add("m1.llm.providers[3].models[1].label", () -> "Colliding model");
        registry.add("m1.llm.providers[3].models[1].recommended", () -> "false");
        for (String env : List.of("TEST_OPENAI_API_KEY", "TEST_GEMINI_API_KEY",
                "TEST_GROQ_API_KEY", "TEST_OPENROUTER_API_KEY")) {
            registry.add(env, () -> switch (env) {
                case "TEST_GEMINI_API_KEY" -> "";
                case "TEST_GROQ_API_KEY" -> SENTINEL;
                default -> "NON_SECRET_" + env;
            });
        }
    }

    private static void provider(
            DynamicPropertyRegistry registry,
            int index,
            String id,
            String label,
            String apiKeyEnv,
            String model,
            String path
    ) {
        String prefix = "m1.llm.providers[" + index + "].";
        registry.add(prefix + "id", () -> id);
        registry.add(prefix + "label", () -> label);
        registry.add(prefix + "enabled", () -> "true");
        registry.add(prefix + "api-key-env", () -> apiKeyEnv);
        registry.add(prefix + "base-url", () -> "http://127.0.0.1:" + fakeProvider.getAddress().getPort() + path);
        registry.add(prefix + "supports-tools", () -> "true");
        registry.add(prefix + "models[0].id", () -> model);
        registry.add(prefix + "models[0].label", () -> model);
        registry.add(prefix + "models[0].recommended", () -> "true");
    }

    private static void startFakeProvider() {
        if (fakeProvider != null) {
            return;
        }
        try {
            fakeProvider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            fakeProvider.createContext("/", exchange -> {
                lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                lastPath.set(exchange.getRequestURI().getPath());
                lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                callCount.incrementAndGet();
                byte[] body;
                int status = responseStatus.get();
                if (status == 200) {
                    body = """
                            {"choices":[{"message":{"content":"OK"}}],
                             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                            """.getBytes(StandardCharsets.UTF_8);
                } else {
                    body = ("{\"error\":{\"message\":\"raw-provider-body-" + SENTINEL + "\"}}")
                            .getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            fakeProvider.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @BeforeEach
    void resetFakeProvider() {
        lastAuthorization.set(null);
        lastPath.set(null);
        lastBody.set(null);
        responseStatus.set(200);
        callCount.set(0);
    }

    @AfterAll
    static void stopFakeProvider() {
        if (fakeProvider != null) {
            fakeProvider.stop(0);
        }
    }

    @Test
    void genericGatewayCallsOpenAiCompatibleTestServer() {
        var result = router.call(new LlmRequest("groq", "groq-test", "system", "hello",
                new LlmSettings(0, 16, 5), List.of()));
        assertEquals("OK", result.text());
        assertEquals("Bearer " + SENTINEL, lastAuthorization.get());
        assertEquals("/groq/v1/chat/completions", lastPath.get());

        router.call(new LlmRequest("openrouter", "openrouter-test", "system", "hello",
                new LlmSettings(0, 16, 5), List.of()));
        assertEquals("Bearer NON_SECRET_TEST_OPENROUTER_API_KEY", lastAuthorization.get());
        assertEquals("/openrouter/v1/chat/completions", lastPath.get());

        router.call(new LlmRequest("openai", "gpt-test", "system", "hello",
                new LlmSettings(0, 16, 5), List.of()));
        assertEquals("Bearer NON_SECRET_TEST_OPENAI_API_KEY", lastAuthorization.get());
        assertEquals("/openai/v1/chat/completions", lastPath.get());
    }

    @Test
    void catalogueReportsProviderCapabilitiesAndNeverExposesKeys() throws Exception {
        String response = mockMvc.perform(get("/api/llm/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].id").value("groq"))
                .andExpect(jsonPath("$[2].supportsTools").value(true))
                .andExpect(jsonPath("$[2].models[0].id").value("groq-test"))
                .andExpect(jsonPath("$[1].available").value(false))
                .andExpect(jsonPath("$[1].reason")
                        .value("Variable TEST_GEMINI_API_KEY absente ou vide dans le processus de M1"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains(SENTINEL));
        assertFalse(response.contains("NON_SECRET_TEST_"));
        assertTrue(response.contains("\"available\":true"));
    }

    @Test
    void versionCreationValidatesProviderModelPairAndInfersOnlyUnambiguousModels() throws Exception {
        long agentId = createAgent();
        String groqVersion = createVersion(agentId, """
                {"provider":"groq","model":"groq-test","systemPrompt":"Test prompt",
                 "configuration":%s}
                """.formatted(configuration()));
        org.junit.jupiter.api.Assertions.assertTrue(groqVersion.contains("\"provider\":\"groq\""));

        mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody("groq", "not-a-model")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody("gemini", "gemini-test")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(null, "shared-test")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MODEL_AMBIGUOUS"));

        mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(null, "groq-test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("groq"));
    }

    @Test
    void executionUsesStoredProviderKeyAndBaseUrlWithoutLeakingSentinel() throws Exception {
        long agentId = createAgent();
        long versionId = createVersionId(agentId, versionBody("groq", "groq-test"));

        mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + versionId + ",\"input\":\"Say OK\","
                                + "\"overrides\":{\"model\":\"openai-test\",\"provider\":\"openai\"}}"))
                .andExpect(status().isUnprocessableEntity());

        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        String response;
        try {
            response = mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + versionId + ",\"input\":\"Say OK\","
                                + "\"overrides\":{\"temperature\":0.4}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.output").value("OK"))
                    .andReturn().getResponse().getContentAsString();
        } finally {
            root.detachAppender(logs);
        }

        assertEquals("Bearer " + SENTINEL, lastAuthorization.get());
        assertEquals("/groq/v1/chat/completions", lastPath.get());
        assertTrue(lastBody.get().contains("\"model\":\"groq-test\""));
        assertFalse(response.contains(SENTINEL));
        assertFalse(logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains(SENTINEL)));
        AgentExecution execution = executions.findAll().stream()
                .filter(row -> "Say OK".equals(row.getInput()))
                .max(java.util.Comparator.comparing(AgentExecution::getId)).orElseThrow();
        assertEquals("groq", execution.getProvider());
        assertEquals("groq-test", execution.getModelUsed());
        assertFalse(objectMapper.writeValueAsString(execution).contains(SENTINEL));
        mockMvc.perform(get("/api/agents/{id}/executions/{executionId}", agentId, execution.getId())
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("groq"))
                .andExpect(jsonPath("$.modelUsed").value("groq-test"));
        assertFalse(outbox.findAll().stream().anyMatch(event -> event.getPayload().contains(SENTINEL)));
        assertFalse(audits.findAll().stream().anyMatch(audit -> audit.getDetails() != null
                && audit.getDetails().contains(SENTINEL)));
    }

    @Test
    void legacyNullProviderIsInferredAndM5CopiesProviderAndModel() throws Exception {
        long agentId = createAgent();
        long baseVersionId = createVersionId(agentId, versionBody("groq", "groq-test"));
        AgentVersion base = versions.findById(baseVersionId).orElseThrow();
        base.setProvider(null);
        versions.saveAndFlush(base);

        mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + baseVersionId + ",\"input\":\"Legacy version\"}"))
                .andExpect(status().isOk());
        assertEquals("Bearer " + SENTINEL, lastAuthorization.get());
        assertEquals("/groq/v1/chat/completions", lastPath.get());

        mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"basedOnVersionId":%d,"source":"M5","studyId":5,
                                 "params":{"temperature":0.4,"provider":"openai","model":"openai-test"}}
                                """.formatted(baseVersionId)))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"basedOnVersionId":%d,"source":"M5","studyId":5,
                                 "params":{"temperature":0.4}}
                                """.formatted(baseVersionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("groq"))
                .andExpect(jsonPath("$.model").value("groq-test"));
    }

    @Test
    void providerCheckIsAdminOnlyRateLimitedAuditedAndDoesNotEchoRawBody() throws Exception {
        int auditCount = audits.findAll().size();
        mockMvc.perform(post("/api/admin/llm/groq/check")
                        .header("X-Dev-User", "ordinary-user")
                        .header("X-Dev-Role", "USER"))
                .andExpect(status().isForbidden());

        responseStatus.set(401);
        String response = mockMvc.perform(post("/api/admin/llm/groq/check")
                        .header("X-Dev-User", "admin-user")
                        .header("X-Dev-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("groq"))
                .andExpect(jsonPath("$.model").value("groq-test"))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_AUTH_FAILED"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains(SENTINEL));
        assertTrue(lastBody.get().contains("\"max_tokens\":16"));
        assertTrue(lastBody.get().contains("Réponds par OK."));
        assertFalse(audits.findAll().stream().skip(auditCount)
                .anyMatch(audit -> audit.getDetails() != null && audit.getDetails().contains(SENTINEL)));
        assertTrue(audits.findAll().stream().skip(auditCount)
                .map(AdminAudit::getAction).anyMatch("LLM_PROVIDER_CHECKED"::equals));

        mockMvc.perform(post("/api/admin/llm/groq/check")
                        .header("X-Dev-User", "admin-user")
                        .header("X-Dev-Role", "ADMIN"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
    }

    private long createAgent() throws Exception {
        String owner = "provider-user";
        String response = mockMvc.perform(post("/api/agents")
                        .header("X-Dev-User", owner)
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Provider test\",\"description\":\"test\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return agents.findByOwnerId(owner).get(0).getId();
    }

    private String createVersion(long agentId, String request) throws Exception {
        return mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private long createVersionId(long agentId, String request) throws Exception {
        String response = createVersion(agentId, request);
        return versions.findByAgentId(agentId).stream()
                .filter(version -> "groq-test".equals(version.getModel()))
                .map(AgentVersion::getId)
                .findFirst().orElseThrow();
    }

    private String versionBody(String provider, String model) {
        String providerField = provider == null ? "" : "\"provider\":\"" + provider + "\",";
        return "{" + providerField + "\"model\":\"" + model
                + "\",\"systemPrompt\":\"Test prompt\",\"configuration\":" + configuration() + "}";
    }

    private String configuration() {
        return """
                {"temperature":0.2,"maxTokens":256,"style":"concise","maxTurns":2,
                 "timeoutSeconds":5,"allowedToolIds":[]}
                """;
    }
}
