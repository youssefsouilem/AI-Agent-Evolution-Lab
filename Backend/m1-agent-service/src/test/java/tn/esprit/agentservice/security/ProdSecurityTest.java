package tn.esprit.agentservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.agentservice.config.SecurityConfig;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentExecution;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.entity.OutboxEvent;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.AgentExecutionRepository;
import tn.esprit.agentservice.repository.AgentVersionRepository;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "m1.llm.strict-model-check=false",
        "spring.datasource.url=jdbc:h2:mem:prod_security;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "m1.outbox.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = "m1.cors.allowed-origins=http://localhost:3000")
class ProdSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AgentRepository agentRepository;
    @Autowired private OutboxEventRepository outboxEvents;
    @Autowired private AgentExecutionRepository executions;
    @Autowired private AgentVersionRepository versions;
    @Autowired private SecurityConfig securityConfig;

    @Test
    void protectedRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/agents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void devIdentityHeadersDoNotAuthenticateTheProductionChain() throws Exception {
        mockMvc.perform(get("/api/agents")
                        .header("X-Dev-User", "root")
                        .header("X-Dev-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredCrossOriginExposesTheExecutionCountHeader() throws Exception {
        mockMvc.perform(options("/api/executions")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Expose-Headers", "X-Total-Count"));
    }

    @Test
    void adminCanReadButCannotModifyAnotherUsersAgent() throws Exception {
        Agent privateAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("supervised agent")
                .ownerId("bob")
                .status(AgentStatus.DRAFT)
                .build());
        var admin = jwt().jwt(token -> token.subject("root"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        mockMvc.perform(get("/api/agents/{id}", privateAgent.getId()).with(admin))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/agents/{id}", privateAgent.getId()).with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"stolen\",\"description\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.errorCode").value("ADMIN_READ_ONLY"));
    }

    @ParameterizedTest
    @MethodSource("agentRouteMatrix")
    void privilegeMatrixCoversEveryAgentRoute(Route route, Caller caller) throws Exception {
        Agent agent = agentRepository.saveAndFlush(Agent.builder()
                .name("matrix agent").ownerId("matrix-owner").status(AgentStatus.ACTIVE).build());
        AgentVersion version = versions.saveAndFlush(AgentVersion.builder()
                .agent(agent).versionNumber(1).model("gpt-4o-mini").systemPrompt("test").active(true)
                .configuration(AgentConfiguration.builder().temperature(0.7).maxTokens(512)
                        .maxTurns(3).timeoutSeconds(30).style("concise").allowedToolIds(List.of()).build())
                .build());
        AgentExecution execution = new AgentExecution();
        execution.setAgentId(agent.getId());
        execution.setVersionId(version.getId());
        execution.setVersionNumber(1);
        execution.setRequestedBy("matrix-owner");
        execution.setInput("matrix input");
        execution.setAppliedSettings("{}");
        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setStartedAt(LocalDateTime.now());
        executions.saveAndFlush(execution);

        RequestPostProcessor identity = switch (caller) {
            case OWNER_USER -> jwt().jwt(token -> token.subject("matrix-owner"));
            case OTHER_USER -> jwt().jwt(token -> token.subject("other-user"));
            case OWNER_ADMIN -> jwt().jwt(token -> token.subject("matrix-owner"))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
            case OTHER_ADMIN -> jwt().jwt(token -> token.subject("root"))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
            case NO_TOKEN -> null;
        };
        MockHttpServletRequestBuilder request = route.request(agent.getId(), version.getId(), execution.getId());
        if (identity != null) {
            request.with(identity);
        }
        ResultActions result = mockMvc.perform(request);

        if (caller == Caller.NO_TOKEN) {
            result.andExpect(status().isUnauthorized());
        } else if (caller == Caller.OTHER_USER) {
            result.andExpect(status().isNotFound());
        } else if (caller == Caller.OTHER_ADMIN && route.write()) {
            result.andExpect(status().isForbidden())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.errorCode").value("ADMIN_READ_ONLY"));
        } else if (caller == Caller.OTHER_ADMIN) {
            result.andExpect(status().isOk());
        } else {
            int actual = result.andReturn().getResponse().getStatus();
            assertTrue(actual != 401 && actual != 403 && actual != 404,
                    route + " should allow its owner, got " + actual);
        }
    }

    static Stream<Arguments> agentRouteMatrix() {
        return Stream.of(Route.values()).flatMap(route -> Stream.of(Caller.values())
                .map(caller -> Arguments.of(route, caller)));
    }

    enum Caller { OWNER_USER, OTHER_USER, OWNER_ADMIN, OTHER_ADMIN, NO_TOKEN }

    enum Route {
        GET_AGENT(false),
        UPDATE_AGENT(true),
        DELETE_AGENT(true),
        GET_VERSIONS(false),
        GET_VERSION(false),
        GET_TUNABLE(false),
        CREATE_VERSION(true),
        ACTIVATE_VERSION(true),
        UPDATE_CONFIGURATION(true),
        EXECUTE(true),
        GET_EXECUTIONS(false),
        GET_EXECUTION(false),
        GET_STATS(false);

        private final boolean write;
        Route(boolean write) { this.write = write; }
        boolean write() { return write; }

        MockHttpServletRequestBuilder request(Long agentId, Long versionId, Long executionId) {
            return switch (this) {
                case GET_AGENT -> get("/api/agents/{id}", agentId);
                case UPDATE_AGENT -> put("/api/agents/{id}", agentId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"updated\",\"description\":\"x\"}");
                case DELETE_AGENT -> delete("/api/agents/{id}", agentId);
                case GET_VERSIONS -> get("/api/agents/{id}/versions", agentId);
                case GET_VERSION -> get("/api/agents/{id}/versions/{vid}", agentId, versionId);
                case GET_TUNABLE -> get("/api/agents/{id}/versions/{vid}/tunable-parameters", agentId, versionId);
                case CREATE_VERSION -> post("/api/agents/{id}/versions", agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basedOnVersionId\":" + versionId + ",\"params\":{}}");
                case ACTIVATE_VERSION -> put("/api/agents/{id}/versions/{vid}/activate", agentId, versionId);
                case UPDATE_CONFIGURATION -> put("/api/agents/{id}/versions/{vid}/configuration",
                        agentId, versionId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature\":0.4,\"maxTokens\":512,\"maxTurns\":3,"
                                + "\"timeoutSeconds\":30,\"style\":\"concise\",\"allowedToolIds\":[]}");
                case EXECUTE -> post("/api/agents/{id}/execute", agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":" + versionId + ",\"input\":\"test\"}");
                case GET_EXECUTIONS -> get("/api/agents/{id}/executions", agentId);
                case GET_EXECUTION -> get("/api/agents/{id}/executions/{executionId}",
                        agentId, executionId);
                case GET_STATS -> get("/api/agents/{id}/stats", agentId);
            };
        }
    }

    @Test
    void anotherUsersAgentIsNotFoundAndCanNotBeDeleted() throws Exception {
        Agent privateAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("private prod test")
                .ownerId("bob")
                .status(AgentStatus.DRAFT)
                .build());

        mockMvc.perform(get("/api/agents/{id}", privateAgent.getId())
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/agents/{id}", privateAgent.getId())
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownershipProtectsUpdateVersionsExecutionAndHistory() throws Exception {
        Agent privateAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("private version test")
                .ownerId("bob")
                .status(AgentStatus.DRAFT)
                .build());
        var caller = jwt().jwt(token -> token.subject("alice"));

        mockMvc.perform(put("/api/agents/{id}", privateAgent.getId()).with(caller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"stolen\",\"description\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agents/{id}/versions", privateAgent.getId()).with(caller))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agents/{id}/versions/{vid}", privateAgent.getId(), 1).with(caller))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/agents/{id}/versions/{vid}/activate", privateAgent.getId(), 1).with(caller))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/agents/{id}/execute", privateAgent.getId()).with(caller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"test\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agents/{id}/executions", privateAgent.getId()).with(caller))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminIsForbiddenFromOutboxAndAdminCanListAllAgents() throws Exception {
        mockMvc.perform(get("/api/admin/outbox")
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agents?all=true")
                        .with(jwt().jwt(token -> token.subject("root"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void agentListAllAndOwnerFilterApplyOnlyToAdmins() throws Exception {
        Agent aliceAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("alice scoped").ownerId("alice").status(AgentStatus.DRAFT).build());
        Agent bobAgent = agentRepository.saveAndFlush(Agent.builder()
                .name("bob scoped").ownerId("bob").status(AgentStatus.DRAFT).build());
        mockMvc.perform(get("/api/agents?all=true&ownerId=bob")
                        .with(jwt().jwt(token -> token.subject("root"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[?(@.id == " + bobAgent.getId() + ")]").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[?(@.id == " + aliceAgent.getId() + ")]").doesNotExist());
        mockMvc.perform(get("/api/agents?all=true&ownerId=bob")
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[?(@.id == " + aliceAgent.getId() + ")]").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[?(@.id == " + bobAgent.getId() + ")]").doesNotExist());
    }

    @Test
    void converterMapsKeycloakRealmAdminRole() {
        JwtAuthenticationConverter converter = securityConfig.keycloakJwtAuthenticationConverter();
        Jwt token = Jwt.withTokenValue("not-a-secret")
                .header("alg", "none")
                .subject("root")
                .claim("realm_access", Map.of("roles", List.of("admin")))
                .build();

        assertTrue(converter.convert(token).getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void profileUsesJwtDisplayNameAndTreatsMissingRoleAsUser() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(token -> token.subject("profile-user")
                        .claim("preferred_username", "profile-name"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.userId").value("profile-user"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.displayName").value("profile-name"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.role").value("USER"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.limits.maxAgents").value(5));
    }

    @Test
    void adminCanRetryFailedOutboxEvent() throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setEventType("AGENT_CREATED");
        event.setRoutingKey("agent.created");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.FAILED);
        event.setAttempts(5);
        event.setCreatedAt(LocalDateTime.now());
        event.setNextAttemptAt(LocalDateTime.now());
        event = outboxEvents.saveAndFlush(event);

        mockMvc.perform(post("/api/admin/outbox/{id}/retry", event.getId())
                        .with(jwt().jwt(token -> token.subject("root"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
        OutboxEvent retried = outboxEvents.findById(event.getId()).orElseThrow();
        assertTrue(retried.getStatus() == OutboxStatus.PENDING);
        assertEquals(0, retried.getAttempts());
    }
}
