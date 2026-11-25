package tn.esprit.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:attempt-limit-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "m1.enabled=false", "orchestrator.enabled=false"
})
@AutoConfigureMockMvc
class WorkflowAttemptLimitTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void serverRejectsExceededLimitAndPreservesStoredWorkflow() throws Exception {
        String settings = """
            {"maxRetries":10,"maxDuration":300,"maxStepDuration":30,
            "maxSteps":30,"maxConcurrent":2,"allowConditions":true}
            """;
        mvc.perform(put("/api/orchestration-settings").contentType(MediaType.APPLICATION_JSON).content(settings))
            .andExpect(status().isOk());
        long id = mapper.readTree(mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"Limites","objective":"Test","selectionMode":"MANUAL","maxAttempts":3}
            """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        String uri = "/api/missions/" + id + "/workflow";
        String graph = """
            {"steps":[{"name":"Analyse","stepOrder":0,"nodeType":"AGENT",
            "agentId":"2","agentVersionId":"21","instruction":"Analyser","maxRetries":10}],"edges":[]}
            """;
        mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON).content(graph)).andExpect(status().isOk());
        mvc.perform(put("/api/orchestration-settings").contentType(MediaType.APPLICATION_JSON)
            .content(settings.replace("\"maxRetries\":10", "\"maxRetries\":4"))).andExpect(status().isOk());
        String before = mvc.perform(get(uri)).andReturn().getResponse().getContentAsString();
        mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON).content(graph))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                "« Analyse » : choisissez entre 1 et 4 tentatives (limite administrateur)."));
        String after = mvc.perform(get(uri)).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(before), mapper.readTree(after));
        mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON)
            .content(graph.replace("\"maxRetries\":10", "\"maxRetries\":4")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.steps[0].maxRetries").value(4));
        // Old requests without an explicit per-step value continue to inherit the setting.
        mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON)
            .content(graph.replace(",\"maxRetries\":10", "")))
            .andExpect(status().isOk());
    }
}
