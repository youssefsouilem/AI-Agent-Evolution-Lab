package tn.esprit.agentservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.agentservice.repository.AgentRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dev_agent_crud;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "m1.outbox.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevAgentCrudSecurityTest {
    private static final String AGENT_JSON =
            "{\"name\":\"azert\",\"description\":\"qqqq\",\"status\":\"DRAFT\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentRepository agents;

    @Test
    void devUsersAndAdminsCanCreateAndManageAgentsAcrossCors() throws Exception {
        mockMvc.perform(post("/api/agents")
                        .header("X-Dev-User", "demo-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("azert"))
                .andExpect(jsonPath("$.ownerId").value("demo-user"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long userAgentId = agents.findByOwnerId("demo-user").stream()
                .filter(agent -> "azert".equals(agent.getName()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/agents")
                        .header("X-Dev-User", "demo-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/agents")
                        .header("X-Dev-User", "demo-admin")
                        .header("X-Dev-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value("demo-admin"));

        mockMvc.perform(post("/api/agents")
                        .header("Origin", "http://localhost:5173")
                        .header("X-Dev-User", "demo-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/agents")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,X-Dev-Role,X-Dev-User"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("X-Dev-Role")));

        mockMvc.perform(put("/api/agents/{id}", userAgentId)
                        .header("X-Dev-User", "demo-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"updated\",\"description\":\"updated\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/agents")
                        .header("X-Dev-User", "demo-user")
                        .header("X-Dev-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"delete me\",\"description\":\"test\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isCreated());
        long disposableId = agents.findByOwnerId("demo-user").stream()
                .filter(agent -> "delete me".equals(agent.getName()))
                .findFirst().orElseThrow().getId();
        mockMvc.perform(delete("/api/agents/{id}", disposableId)
                        .header("X-Dev-User", "demo-user")
                        .header("X-Dev-Role", "USER"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/agents")
                        .header("X-Dev-User", "demo-user")
                        .header("X-Dev-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..name",
                        org.hamcrest.Matchers.hasItem("updated")));
    }

    @Test
    void devAuthorizationRejectionsUseStandardJsonErrorResponse() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("X-Dev-User", "demo-user")
                        .header("X-Dev-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Accès interdit."))
                .andExpect(jsonPath("$.path").value("/api/admin/users"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.errorCode.enum",
                        org.hamcrest.Matchers.hasItems("ACCESS_DENIED", "UNAUTHENTICATED")));
    }
}
