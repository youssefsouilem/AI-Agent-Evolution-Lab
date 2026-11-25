package tn.esprit.agentservice;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:provider_resolution_api;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "m1.outbox.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProviderResolutionApiTest {
    private static final String TEST_KEY = "runtime-test-token";
    private static final AtomicReference<String> geminiKey =
            new AtomicReference<>("  \"" + TEST_KEY + "\"  ");
    private static final AtomicReference<String> responseText = new AtomicReference<>("OK");
    private static final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private static final AtomicInteger responseStatus = new AtomicInteger(200);
    private static HttpServer fakeProvider;

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentRepository agents;
    @Autowired private AgentVersionRepository versions;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void providerSettings(DynamicPropertyRegistry registry) {
        startFakeProvider();
        registry.add("OPENAI_API_KEY", () -> "");
        registry.add("GEMINI_API_KEY", geminiKey::get);
        registry.add("GROQ_API_KEY", () -> "");
        registry.add("OPENROUTER_API_KEY", () -> "");
        provider(registry, 0, "openai", "OpenAI", "OPENAI_API_KEY", "gpt-4o-mini", "/openai/v1");
        provider(registry, 1, "gemini", "Google Gemini", "GEMINI_API_KEY",
                "gemini-2.0-flash", "/gemini/v1");
        provider(registry, 2, "groq", "Groq", "GROQ_API_KEY",
                "llama-3.3-70b-versatile", "/groq/v1");
        provider(registry, 3, "openrouter", "OpenRouter", "OPENROUTER_API_KEY",
                "openai/gpt-4o-mini", "/openrouter/v1");
    }

    private static void provider(
            DynamicPropertyRegistry registry,
            int index,
            String id,
            String label,
            String keyEnv,
            String model,
            String path
    ) {
        String prefix = "m1.llm.providers[" + index + "].";
        registry.add(prefix + "id", () -> id);
        registry.add(prefix + "label", () -> label);
        registry.add(prefix + "enabled", () -> "true");
        registry.add(prefix + "api-key-env", () -> keyEnv);
        registry.add(prefix + "base-url",
                () -> "http://127.0.0.1:" + fakeProvider.getAddress().getPort() + path);
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
                lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String body = new ObjectMapper().writeValueAsString(Map.of(
                        "choices", new Object[] {Map.of("message", Map.of("content", responseText.get()))},
                        "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)
                ));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            fakeProvider.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @BeforeEach
    void resetProvider() {
        geminiKey.set("  \"" + TEST_KEY + "\"  ");
        responseText.set("OK");
        responseStatus.set(200);
        lastAuthorization.set(null);
        lastPath.set(null);
        lastRequestBody.set(null);
    }

    @AfterAll
    static void stopFakeProvider() {
        if (fakeProvider != null) {
            fakeProvider.stop(0);
        }
    }

    @Test
    void assistAndExecuteUseGeminiWithTheTrimmedRuntimeKeyAndDefaultInstruction() throws Exception {
        responseText.set("""
                {"name":"Assistant proposé","description":"Aide","systemPrompt":"Aide",
                 "model":"gemini-2.0-flash","temperature":0.7,"maxTokens":1024,
                 "maxTurns":3,"timeoutSeconds":30,"style":"concise"}
                """);
        mockMvc.perform(post("/api/agents/assist")
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Aide\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("gemini-2.0-flash"))
                .andExpect(jsonPath("$.provider").value("gemini"));

        assertTrue(("Bearer " + TEST_KEY).equals(lastAuthorization.get()),
                "The provider authorization header was not normalized.");
        assertTrue(lastRequestBody.get().contains("\"model\":\"gemini-2.0-flash\""),
                "Assist did not call the Gemini model.");
        assertTrue("/gemini/v1/chat/completions".equals(lastPath.get()),
                "Assist did not use the Gemini provider endpoint.");

        long agentId = createAgent();
        String version = mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"gemini","model":"gemini-2.0-flash","systemPrompt":" "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.systemPrompt").value(" "))
                .andExpect(jsonPath("$.configuration.temperature").value(0.7))
                .andExpect(jsonPath("$.configuration.maxTokens").value(1024))
                .andExpect(jsonPath("$.configuration.maxTurns").value(3))
                .andExpect(jsonPath("$.configuration.timeoutSeconds").value(30))
                .andExpect(jsonPath("$.configuration.style").value("concise"))
                .andExpect(jsonPath("$.configuration.allowedToolIds").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long versionId = objectMapper.readTree(version).path("id").asLong();

        mockMvc.perform(put("/api/agents/{id}/versions/{vid}/activate", agentId, versionId)
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + versionId + ",\"input\":\"Say OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertTrue(lastRequestBody.get().contains("\"model\":\"gemini-2.0-flash\""),
                "Execute did not call the Gemini model.");
        assertTrue("/gemini/v1/chat/completions".equals(lastPath.get()),
                "Execute did not use the Gemini provider endpoint.");
        assertTrue(lastRequestBody.get().contains("\"content\":\"Tu es un assistant utile.\""),
                "Execution did not use the neutral default instruction.");
        assertFalse(version.contains("Tu es un assistant utile."),
                "The runtime-only default instruction was persisted.");
    }

    @Test
    void emptyRuntimeKeyMakesAssistAndExecuteReportProviderNotConfigured() throws Exception {
        long agentId = createAgent();
        long versionId = createGeminiVersion(agentId, "Aide");
        geminiKey.set("  '  \"  '  ");

        mockMvc.perform(get("/api/llm/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].available").value(false))
                .andExpect(jsonPath("$[1].reason")
                        .value("Variable GEMINI_API_KEY absente ou vide dans le processus de M1"));

        mockMvc.perform(post("/api/agents/assist")
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Aide\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_NOT_CONFIGURED"));

        mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + versionId + ",\"input\":\"Aide\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_NOT_CONFIGURED"));
    }

    @Test
    void executionRejectsUnknownModelWithModelSpecificError() throws Exception {
        long agentId = createAgent();
        AgentVersion version = versions.saveAndFlush(AgentVersion.builder()
                .agent(agents.findById(agentId).orElseThrow())
                .versionNumber(1)
                .provider("gemini")
                .model("unknown-model-test")
                .systemPrompt("Aide")
                .active(true)
                .configuration(defaultConfiguration())
                .build());

        mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + version.getId() + ",\"input\":\"Aide\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("model"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("unknown-model-test"),
                        org.hamcrest.Matchers.containsString("liste actuelle"))));
    }

    @Test
    void providerModelRejectionIsClassifiedWithoutReturningProviderBody() throws Exception {
        long agentId = createAgent();
        long versionId = createGeminiVersion(agentId, "Aide");
        responseStatus.set(404);
        responseText.set("provider-error-with-sensitive-test-material");

        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        String response;
        try {
            response = mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                            .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"versionId\":" + versionId + ",\"input\":\"Aide\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_AVAILABLE"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("model"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString("gemini-2.0-flash"),
                            org.hamcrest.Matchers.containsString("liste actuelle"))))
                    .andReturn().getResponse().getContentAsString();
        } finally {
            root.detachAppender(logs);
        }
        assertFalse(response.contains(TEST_KEY));
        assertFalse(response.contains("provider-error-with-sensitive-test-material"));
        String formattedLogs = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (first, next) -> first + next);
        assertFalse(formattedLogs.contains(TEST_KEY));
    }

    @Test
    void provider400NamingTheRequestedModelIsClassifiedAsUnavailable() throws Exception {
        long agentId = createAgent();
        long versionId = createGeminiVersion(agentId, "Aide");
        responseStatus.set(400);
        responseText.set("Unsupported model gemini-2.0-flash");

        mockMvc.perform(post("/api/agents/{id}/execute", agentId)
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + versionId + ",\"input\":\"Aide\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("model"));
    }

    @Test
    void assistReturnsSafeProviderAuthenticationErrorInsteadOfInternalServerError() throws Exception {
        responseStatus.set(401);
        responseText.set("provider-error-with-sensitive-test-material");

        String response = mockMvc.perform(post("/api/agents/assist")
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Propose un agent football\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "Le fournisseur du modèle a refusé l'authentification. Vérifiez la configuration de sa clé API."))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains(TEST_KEY));
        assertFalse(response.contains("provider-error-with-sensitive-test-material"));
    }

    @Test
    void invalidVersionAndAgentBodiesContainFrenchFieldErrorsAndNoKeyMaterialInOutput() throws Exception {
        long agentId = createAgent();
        mockMvc.perform(post("/api/agents/{id}/versions", agentId)
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"gemini","model":"gemini-2.0-flash","systemPrompt":"Aide",
                                 "configuration":{"temperature":3,"maxTokens":1024,"maxTurns":3,
                                   "timeoutSeconds":30,"style":"concise","allowedToolIds":[]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("configuration.temperature"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());

        mockMvc.perform(post("/api/agents")
                        .header("X-Dev-User", "provider-owner").header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());

        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        String response;
        try {
            response = mockMvc.perform(get("/api/llm/models"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        } finally {
            root.detachAppender(logs);
        }
        String formattedLogs = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (first, next) -> first + next);
        assertFalse(response.contains(TEST_KEY), "The catalogue exposed key material.");
        assertFalse(formattedLogs.contains(TEST_KEY), "Application logs exposed key material.");

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.fieldErrors.items.$ref")
                        .value("#/components/schemas/FieldError"))
                .andExpect(jsonPath("$.components.schemas.FieldError.required[0]").value("field"))
                .andExpect(jsonPath("$.components.schemas.FieldError.required[1]").value("message"));
    }

    private long createAgent() {
        return agents.saveAndFlush(Agent.builder().name("Provider test agent")
                .ownerId("provider-owner").status(AgentStatus.DRAFT).build()).getId();
    }

    private long createGeminiVersion(long agentId, String prompt) {
        return versions.saveAndFlush(AgentVersion.builder()
                .agent(agents.findById(agentId).orElseThrow())
                .versionNumber(1)
                .provider("gemini")
                .model("gemini-2.0-flash")
                .systemPrompt(prompt)
                .active(true)
                .configuration(defaultConfiguration())
                .build()).getId();
    }

    private AgentConfiguration defaultConfiguration() {
        return AgentConfiguration.builder().temperature(0.7).maxTokens(1024).maxTurns(3)
                .timeoutSeconds(30).style("concise").allowedToolIds(java.util.List.of()).build();
    }
}
