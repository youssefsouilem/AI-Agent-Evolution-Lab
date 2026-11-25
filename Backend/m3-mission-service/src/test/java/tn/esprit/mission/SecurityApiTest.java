package tn.esprit.mission;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:security-audit;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "security.enabled=true",
    "orchestrator.enabled=false",
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
  }
)
@AutoConfigureMockMvc
class SecurityApiTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper mapper;

  @MockitoBean
  JwtDecoder jwtDecoder;

  void token(String token, String subject, String role) {
    when(jwtDecoder.decode(token)).thenReturn(
      Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject(subject)
        .claim("realm_access", Map.of("roles", List.of(role)))
        .build()
    );
  }

  @Test
  void anonymousRequestIsRejectedAndUserCannotChangeAdminSettings() throws Exception {
    mvc
      .perform(get("/actuator/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/missions")).andExpect(status().isUnauthorized());
    token("user-token", "alice", "user");
    mvc
      .perform(
        put("/api/orchestration-settings")
          .header("Authorization", "Bearer user-token")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"maxRetries":3,"maxDuration":300,"maxStepDuration":30,"maxSteps":30,"maxConcurrent":2,"allowConditions":true}
            """
          )
      )
      .andExpect(status().isForbidden());
    token("admin-token", "admin", "admin");
    mvc
      .perform(
        get("/api/orchestration-settings").header(
          "Authorization",
          "Bearer admin-token"
        )
      )
      .andExpect(status().isOk());
  }

  @Test
  void ownerComesFromJwtAndOtherUsersCannotReadOrExecuteMission() throws Exception {
    token("alice-token", "alice", "user");
    token("bob-token", "bob", "user");
    token("admin-token", "admin", "admin");
    var response = mvc
      .perform(
        post("/api/missions")
          .header("Authorization", "Bearer alice-token")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"name":"Mission privée","objective":"Test","selectionMode":"MANUAL","maxAttempts":2,"ownerId":"bob"}
            """
          )
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.ownerId").value("alice"))
      .andReturn();
    long id = mapper
      .readTree(response.getResponse().getContentAsString())
      .get("id")
      .asLong();
    mvc
      .perform(
        get("/api/missions/{id}", id).header(
          "Authorization",
          "Bearer bob-token"
        )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/api/missions/{id}/execute", id).header(
          "Authorization",
          "Bearer bob-token"
        )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        get("/api/missions/{id}", id).header(
          "Authorization",
          "Bearer admin-token"
        )
      )
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/missions").header("Authorization", "Bearer bob-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(0));
  }
}
