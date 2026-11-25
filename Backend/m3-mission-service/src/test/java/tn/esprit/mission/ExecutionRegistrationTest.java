package tn.esprit.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:execution-registration-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "m1.enabled=false", "orchestrator.enabled=false"
})
@AutoConfigureMockMvc
class ExecutionRegistrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void pendingExecutionAndStepsAreCommittedAndDuplicateLaunchIsRejected() throws Exception {
        long missionId = createMission();
        String graph = graph();
        saveWorkflow(missionId, graph);
        var result = mvc.perform(post("/api/missions/{id}/execute", missionId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.requestedAt").isNotEmpty())
            .andExpect(jsonPath("$.startedAt").isEmpty())
            .andExpect(jsonPath("$.attemptsUsed").value(0)).andReturn();
        long executionId = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertEquals("/api/executions/" + executionId, result.getResponse().getHeader("Location"));
        mvc.perform(get("/api/executions/{id}", executionId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/executions/{id}/steps", executionId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].attemptNumber").value(0));
        mvc.perform(get("/api/missions/{id}/executions", missionId))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(executionId));
        mvc.perform(post("/api/missions/{id}/execute", missionId)).andExpect(status().isConflict());
        assertEquals(2, jdbc.queryForObject(
            "select count(*) from mission_step_executions where mission_execution_id = ?", Integer.class, executionId));
        mvc.perform(put("/api/missions/{id}/workflow", missionId).contentType(MediaType.APPLICATION_JSON).content(graph))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/missions/999999/execute")).andExpect(status().isNotFound());
        mvc.perform(get("/api/executions/999999/steps")).andExpect(status().isNotFound());
    }

    @Test
    void invalidDraftOrLoweredLimitDoesNotCreateAnExecution() throws Exception {
        long missionId = createMission();
        mvc.perform(post("/api/missions/{id}/execute", missionId)).andExpect(status().isBadRequest());
        saveWorkflow(missionId, graph().replace("\"instruction\":\"Analyser\"", "\"instruction\":\" \""));
        mvc.perform(post("/api/missions/{id}/execute", missionId)).andExpect(status().isBadRequest());
        saveWorkflow(missionId, graph().replace("\"agentVersionId\":\"11\"", "\"agentVersionId\":null"));
        mvc.perform(post("/api/missions/{id}/execute", missionId)).andExpect(status().isBadRequest());
        saveWorkflow(missionId, graph());
        mvc.perform(put("/api/orchestration-settings").contentType(MediaType.APPLICATION_JSON).content("""
            {"maxRetries":1,"maxDuration":300,"maxStepDuration":30,"maxSteps":30,"maxConcurrent":2,"allowConditions":true}
            """)).andExpect(status().isOk());
        mvc.perform(post("/api/missions/{id}/execute", missionId)).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from mission_executions where mission_id = ?", Integer.class, missionId));
        mvc.perform(put("/api/orchestration-settings").contentType(MediaType.APPLICATION_JSON).content("""
            {"maxRetries":3,"maxDuration":300,"maxStepDuration":30,"maxSteps":30,"maxConcurrent":2,"allowConditions":true}
            """)).andExpect(status().isOk());
    }

    @Test
    void concurrentRequestsCreateOnlyOnePendingExecution() throws Exception {
        long missionId = createMission();
        saveWorkflow(missionId, graph());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.of(
                (java.util.concurrent.Callable<Integer>) () -> mvc.perform(post("/api/missions/{id}/execute", missionId)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(post("/api/missions/{id}/execute", missionId)).andReturn().getResponse().getStatus()
            ));
            var statuses = List.of(results.get(0).get(), results.get(1).get());
            assertTrue(statuses.contains(202));
            assertTrue(statuses.contains(409));
            assertEquals(1, jdbc.queryForObject(
                "select count(*) from mission_executions where mission_id = ?", Integer.class, missionId));
        } finally { executor.shutdownNow(); }
    }

    private long createMission() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"Exécution test","objective":"Test","selectionMode":"MANUAL","maxAttempts":3}
            """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void saveWorkflow(long id, String graph) throws Exception {
        mvc.perform(put("/api/missions/{id}/workflow", id).contentType(MediaType.APPLICATION_JSON).content(graph))
            .andExpect(status().isOk());
    }

    private String graph() {
        return """
            {"steps":[
              {"name":"Préparer","stepOrder":0,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"Préparer","maxRetries":2},
              {"name":"Analyser","stepOrder":1,"nodeType":"AGENT","agentId":"2","agentVersionId":"21","instruction":"Analyser","maxRetries":2}
            ],"edges":[{"sourceStepOrder":0,"targetStepOrder":1}]}
            """;
    }
}
