package tn.esprit.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
    "spring.datasource.url=jdbc:h2:mem:settings-api-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "m1.enabled=false", "orchestrator.enabled=false"
})
@AutoConfigureMockMvc
class OrchestrationSettingsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void defaultsPersistenceSingleRowAndInvalidUpdates() throws Exception {
        String uri = "/api/orchestration-settings";

        mvc.perform(get(uri)).andExpect(status().isOk())
            .andExpect(jsonPath("$.maxRetries").value(3))
            .andExpect(jsonPath("$.maxDuration").value(300))
            .andExpect(jsonPath("$.maxStepDuration").value(30))
            .andExpect(jsonPath("$.maxSteps").value(30))
            .andExpect(jsonPath("$.maxConcurrent").value(2))
            .andExpect(jsonPath("$.allowConditions").value(true));
        assertEquals(0, rowCount());

        var values = mapper.createObjectNode()
            .put("maxRetries", 4).put("maxDuration", 600)
            .put("maxStepDuration", 40).put("maxSteps", 20)
            .put("maxConcurrent", 3).put("allowConditions", false);
        mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON).content(values.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.maxRetries").value(4));
        assertEquals(1, rowCount());
        assertEquals(4, jdbc.queryForObject(
            "select max_retries from orchestration_settings where id = 1", Integer.class));
        mvc.perform(get(uri)).andExpect(status().isOk())
            .andExpect(content().json(values.toString()));

        values.put("maxRetries", 5);
        mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON).content(values.toString()))
            .andExpect(status().isOk());
        assertEquals(1, rowCount());

        // A fresh request must read committed database values, not browser or service state.
        jdbc.update("update orchestration_settings set max_retries = 6 where id = 1");
        mvc.perform(get(uri)).andExpect(status().isOk())
            .andExpect(jsonPath("$.maxRetries").value(6));

        for (var entry : Map.of(
            "maxRetries", new int[] {0, 11},
            "maxDuration", new int[] {9, 3601},
            "maxStepDuration", new int[] {0, 601},
            "maxSteps", new int[] {0, 101},
            "maxConcurrent", new int[] {0, 11}
        ).entrySet()) {
            for (int invalidValue : entry.getValue()) {
                var invalid = values.deepCopy().put(entry.getKey(), invalidValue);
                mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
            }
        }

        for (String field : new String[] {
            "maxRetries", "maxDuration", "maxStepDuration", "maxSteps", "maxConcurrent", "allowConditions"
        }) {
            var invalid = values.deepCopy();
            invalid.remove(field);
            mvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
                .andExpect(status().isBadRequest());
        }
        assertEquals(1, rowCount());
        mvc.perform(get(uri)).andExpect(status().isOk())
            .andExpect(jsonPath("$.maxRetries").value(6))
            .andExpect(jsonPath("$.allowConditions").value(false));
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from orchestration_settings", Integer.class);
    }
}
