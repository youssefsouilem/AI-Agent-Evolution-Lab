package tn.esprit.mission;

import com.fasterxml.jackson.databind.JsonNode;
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
    "spring.datasource.url=jdbc:h2:mem:mission-api-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "m1.enabled=false", "orchestrator.enabled=false"
})
@AutoConfigureMockMvc
class MissionApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private long create(String name) throws Exception {
        var request = mapper.createObjectNode().put("name", name)
            .put("objective", "Objectif intégration").put("selectionMode", "MANUAL")
            .put("maxAttempts", 3).put("ownerId", "test");
        return mapper.readTree(mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON)
            .content(request.toString())).andExpect(status().isCreated()).andReturn()
            .getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void createReadUpdateSearchPaginationAndDelete() throws Exception {
        long id = create("API-CRUD-unique");
        mvc.perform(get("/api/missions/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("API-CRUD-unique"));
        mvc.perform(put("/api/missions/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"API-CRUD-renommée","objective":"Objectif modifié","selectionMode":"AUTOMATIC_AI","maxAttempts":4}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.objective").value("Objectif modifié"));
        mvc.perform(get("/api/missions").param("search", "api-crud-renommée").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/missions").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON).content("""
            {"name":" ","objective":"test","selectionMode":"MANUAL","maxAttempts":3}
            """)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/missions/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/missions/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void twoConnectedAgentsSurviveReloadReplacementAndIndependentDuplication() throws Exception {
        long id = create("API-workflow-unique");
        String body = """
            {"steps":[
              {"name":"Préparation","stepOrder":0,"nodeType":"AGENT","agentId":"1","agentVersionId":"11","instruction":"Préparer les données","inputMapping":"CSV","expectedOutput":"Données propres","positionX":-25.5,"positionY":150,"maxRetries":2,"agentRole":"Préparateur"},
              {"name":"Analyse","stepOrder":1,"nodeType":"AGENT","agentId":"2","agentVersionId":"21","instruction":"Analyser les données","positionX":450,"positionY":200,"maxRetries":3}
            ],"edges":[{"sourceStepOrder":0,"targetStepOrder":1}]}
            """;
        mvc.perform(put("/api/missions/{id}/workflow", id).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        JsonNode original = workflow(id);
        assertEquals("11", original.at("/steps/0/agentVersionId").asText());
        assertEquals("21", original.at("/steps/1/agentVersionId").asText());
        assertEquals("Préparer les données", original.at("/steps/0/instruction").asText());
        assertEquals(-25.5, original.at("/steps/0/positionX").asDouble());
        assertEquals(original.at("/steps/0/id").asLong(), original.at("/edges/0/sourceStepId").asLong());
        assertEquals(original.at("/steps/1/id").asLong(), original.at("/edges/0/targetStepId").asLong());
        long copy = mapper.readTree(mvc.perform(post("/api/missions/{id}/duplicate", id))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode cloned = workflow(copy);
        assertNotEquals(original.at("/steps/0/id"), cloned.at("/steps/0/id"));
        assertEquals("11", cloned.at("/steps/0/agentVersionId").asText());
        assertEquals(cloned.at("/steps/1/id"), cloned.at("/edges/0/targetStepId"));
        mvc.perform(put("/api/missions/{id}/workflow", copy).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("Préparer les données", "Instruction copie"))).andExpect(status().isOk());
        assertEquals("Préparer les données", workflow(id).at("/steps/0/instruction").asText());
        // An invalid save must preserve the previously saved graph.
        mvc.perform(put("/api/missions/{id}/workflow", id).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("\"targetStepOrder\":1", "\"targetStepOrder\":9999999999")))
            .andExpect(status().isBadRequest());
        assertEquals(original, workflow(id));
        mvc.perform(put("/api/missions/{id}/workflow", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"steps\":[null],\"edges\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/missions/{id}/workflow", id).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("Préparation", "x".repeat(161)))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/missions/{id}", copy)).andExpect(status().isNoContent());
        mvc.perform(get("/api/missions/{id}/workflow", copy)).andExpect(status().isNotFound());
        assertEquals(2, workflow(id).get("steps").size());
        mvc.perform(delete("/api/missions/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void maximumLengthMissionCanBeDuplicated() throws Exception {
        long id = create("L".repeat(160));
        var copy = mapper.readTree(mvc.perform(post("/api/missions/{id}/duplicate", id))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertEquals(160, copy.get("name").asText().length());
        assertTrue(copy.get("name").asText().endsWith(" (copie)"));
        mvc.perform(delete("/api/missions/{id}", copy.get("id").asLong())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/missions/{id}", id)).andExpect(status().isNoContent());
    }

    private JsonNode workflow(long id) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/missions/{id}/workflow", id))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
