package tn.esprit.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.mission.controller.AgentCatalogController;
import tn.esprit.mission.service.AgentCatalogService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentCatalogTest {

    @Test
    void returnsActualResourceWithVersionsAndConfiguration() throws Exception {
        var service = new AgentCatalogService(new ObjectMapper(), null, false);
        var mvc = MockMvcBuilders.standaloneSetup(new AgentCatalogController(service)).build();

        mvc.perform(get("/api/missions/agent-catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(10))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].versions[0].id").value(11))
            .andExpect(jsonPath("$[0].versions[0].configuration.timeoutSeconds").value(30));
    }
}
