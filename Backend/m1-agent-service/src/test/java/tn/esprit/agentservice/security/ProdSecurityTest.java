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
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.agentservice.config.SecurityConfig;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentStatus;
import tn.esprit.agentservice.entity.OutboxEvent;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AgentRepository agentRepository;
    @Autowired private OutboxEventRepository outboxEvents;
    @Autowired private SecurityConfig securityConfig;

    @Test
    void protectedRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/agents")).andExpect(status().isUnauthorized());
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
