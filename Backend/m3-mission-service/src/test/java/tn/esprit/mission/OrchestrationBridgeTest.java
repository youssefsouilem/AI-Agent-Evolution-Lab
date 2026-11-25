package tn.esprit.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tn.esprit.mission.dto.EngineRequest;
import tn.esprit.mission.service.OrchestratorClient;
import tn.esprit.mission.repository.MissionExecutionRepository;
import tn.esprit.mission.service.ExecutionStateStore;
import tn.esprit.mission.enums.ExecutionStatus;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:bridge-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "m1.enabled=false"
})
@AutoConfigureMockMvc
class OrchestrationBridgeTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MissionExecutionRepository repository;
    @Autowired ExecutionStateStore store;
    @MockitoBean OrchestratorClient client;

    @Test
    void successfulEngineResponsePersistsStepsAndCandidateWithoutPublishing() throws Exception {
        when(client.enabled()).thenReturn(true);
        when(client.execute(any())).thenAnswer(call -> {
            EngineRequest request = call.getArgument(0);
            assertEquals("11", request.stages().get(0).agentVersionId());
            var step = request.stages().get(0).id();
            var output = Map.of("status", "SUCCESS", "output", "Analyse produite", "tokensUsed", 17,
                "startedAt", "2026-11-21T12:00:00Z", "finishedAt", "2026-11-21T12:00:01Z");
            return mapper.valueToTree(Map.of("execution_id", request.executionId(), "status", "COMPLETED",
                "simulated", true, "state", Map.of("mission_id", request.missionId(), "results",
                    List.of(Map.of("stepId", step, "attempt", 1, "output", output)), "outputs", Map.of(step, output))));
        });
        long id = launch();
        await(id, ExecutionStatus.COMPLETED);
        var saved = repository.findById(id).orElseThrow();
        assertEquals("Analyse produite", saved.getCandidateResult());
        assertNull(saved.getFinalResult());
        assertEquals("PENDING_MONITORING", saved.getPublicationStatus());
        assertEquals("SIMULATED", saved.getMetricsSource());
        assertEquals(17L, saved.getTotalTokensUsed());
        mvc.perform(get("/api/executions/{id}/steps", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[0].output").value("Analyse produite"));
        var malformed = mapper.readTree("{\"execution_id\":\"" + id + "\",\"status\":\"COMPLETED\",\"state\":{\"mission_id\":-1,\"results\":[]}}");
        assertThrows(IllegalArgumentException.class, () -> store.apply(id, malformed));
    }

    @Test
    void unavailableEngineRequiresRecoveryAndNeverReportsSuccess() throws Exception {
        when(client.enabled()).thenReturn(true);
        when(client.execute(any())).thenThrow(new IllegalStateException("connection lost"));
        when(client.get(any())).thenThrow(new IllegalStateException("offline"));
        long id = launch();
        await(id, ExecutionStatus.RECOVERY_REQUIRED);
        assertNull(repository.findById(id).orElseThrow().getCandidateResult());
        assertNull(repository.findById(id).orElseThrow().getFinalResult());
    }

    @Test
    void incompleteSuccessIsRejected() throws Exception {
        when(client.enabled()).thenReturn(true);
        when(client.execute(any())).thenAnswer(call -> {
            EngineRequest request = call.getArgument(0);
            return mapper.valueToTree(Map.of("execution_id", request.executionId(), "status", "COMPLETED",
                "state", Map.of("mission_id", request.missionId(), "results", List.of(), "outputs", Map.of())));
        });
        when(client.get(any())).thenThrow(new IllegalStateException("offline"));
        long id = launch();
        await(id, ExecutionStatus.RECOVERY_REQUIRED);
        assertNull(repository.findById(id).orElseThrow().getCandidateResult());
    }

    @Test
    void conditionalSnapshotPersistsSkippedBranchAndCommonResult() throws Exception {
        when(client.enabled()).thenReturn(true);
        when(client.execute(any())).thenAnswer(call -> {
            EngineRequest request = call.getArgument(0);
            var ids = request.stages().stream().map(EngineRequest.Stage::id).toList();
            var output = Map.of("status", "SUCCESS", "output", "Résultat commun", "tokensUsed", 0);
            var results = List.of(
                Map.of("stepId", ids.get(0), "output", output),
                Map.of("stepId", ids.get(2), "output", output),
                Map.of("stepId", ids.get(4), "output", output));
            return mapper.valueToTree(Map.of("execution_id", request.executionId(), "status", "COMPLETED",
                "simulated", true, "state", Map.of("mission_id", request.missionId(), "results", results,
                    "skipped_steps", List.of(ids.get(3)), "executed_steps", List.of(ids.get(0), ids.get(1), ids.get(2), ids.get(4)),
                    "control_results", Map.of(ids.get(1), Map.of("actual", 80, "branch", "yes")),
                    "outputs", Map.of(ids.get(0), output, ids.get(2), output, ids.get(4), output))));
        });
        long mission = mapper.readTree(mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Condition\",\"objective\":\"Test\",\"selectionMode\":\"MANUAL\",\"maxAttempts\":2}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/missions/{id}/workflow", mission).contentType(MediaType.APPLICATION_JSON).content("""
            {"steps":[
                {"name":"A","stepOrder":0,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"A","maxRetries":2},
                {"name":"If","stepOrder":1,"nodeType":"CONDITION","conditionVariable":"score","conditionOperator":">=","conditionExpected":70,"conditionSampleValue":80},
                {"name":"Oui","stepOrder":2,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"Oui","maxRetries":2},
                {"name":"Non","stepOrder":3,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"Non","maxRetries":2},
                {"name":"Commun","stepOrder":4,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"Commun","maxRetries":2}
            ],"edges":[
                {"sourceStepOrder":0,"targetStepOrder":1},
                {"sourceStepOrder":1,"targetStepOrder":2,"conditionLabel":"yes"},
                {"sourceStepOrder":1,"targetStepOrder":3,"conditionLabel":"no"},
                {"sourceStepOrder":2,"targetStepOrder":4},{"sourceStepOrder":3,"targetStepOrder":4}
            ]}
            """)).andExpect(status().isOk());
        long id = mapper.readTree(mvc.perform(post("/api/missions/{id}/execute", mission))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        await(id, ExecutionStatus.COMPLETED);
        mvc.perform(get("/api/executions/{id}/steps", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$[2].status").value("COMPLETED"))
            .andExpect(jsonPath("$[3].status").value("SKIPPED"))
            .andExpect(jsonPath("$[3].attemptNumber").value(0))
            .andExpect(jsonPath("$[4].status").value("COMPLETED"));
        assertEquals("Résultat commun", repository.findById(id).orElseThrow().getCandidateResult());
        assertEquals(3, repository.findById(id).orElseThrow().getAttemptsUsed());
    }

    private long launch() throws Exception {
        long mission = mapper.readTree(mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Bridge test\",\"objective\":\"Analyse\",\"selectionMode\":\"MANUAL\",\"maxAttempts\":2}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/missions/{id}/workflow", mission).contentType(MediaType.APPLICATION_JSON).content("""
            {"steps":[{"name":"Analyse","stepOrder":0,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"Analyser","maxRetries":2}],"edges":[]}
            """)).andExpect(status().isOk());
        return mapper.readTree(mvc.perform(post("/api/missions/{id}/execute", mission))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void await(long id, ExecutionStatus expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (repository.findById(id).orElseThrow().getStatus() == expected) return;
            Thread.sleep(20);
        }
        assertEquals(expected, repository.findById(id).orElseThrow().getStatus());
    }
}
