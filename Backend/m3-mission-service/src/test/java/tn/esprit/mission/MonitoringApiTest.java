package tn.esprit.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.mission.controller.MonitoringController;
import tn.esprit.mission.enums.AnomalySeverity;
import tn.esprit.mission.exception.ApiExceptionHandler;
import tn.esprit.mission.exception.MonitoringContractException;
import tn.esprit.mission.exception.MonitoringUnavailableException;
import tn.esprit.mission.service.M6MonitoringClient;
import tn.esprit.mission.service.MonitoringService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MonitoringApiTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String metrics = """
        {"executionId":31,"durationMs":2400,"tokensUsed":850,"attempts":2,"status":"COMPLETED"}
        """;

    @Test
    void mockScenariosAreExplicitAndMetricsAreValidated() throws Exception {
        for (var severity : AnomalySeverity.values()) {
            var service = new MonitoringService(null, mapper, false, severity);
            var mvc = MockMvcBuilders.standaloneSetup(new MonitoringController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
            mvc.perform(post("/api/monitoring/check").contentType(MediaType.APPLICATION_JSON).content(metrics))
                .andExpect(status().isOk()).andExpect(jsonPath("$.executionId").value(31))
                .andExpect(jsonPath("$.source").value("SIMULATION"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.severity").value(severity.name()))
                .andExpect(jsonPath("$.blocking").value(severity == AnomalySeverity.CRITICAL));
            mvc.perform(post("/api/monitoring/check").contentType(MediaType.APPLICATION_JSON)
                .content(metrics.replace("\"tokensUsed\":850", "\"tokensUsed\":-1")))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/monitoring/check").contentType(MediaType.APPLICATION_JSON)
                .content(metrics.replace("COMPLETED", "PENDING")))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/monitoring/check").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void unavailableAndIncompatibleServicesHaveExplicitHttpErrors() throws Exception {
        var client = mock(M6MonitoringClient.class);
        var service = new MonitoringService(client, mapper, true, AnomalySeverity.NORMAL);
        var mvc = MockMvcBuilders.standaloneSetup(new MonitoringController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        doThrow(new MonitoringUnavailableException("M6 indisponible", null)).when(client).detect(any());
        mvc.perform(post("/api/monitoring/check").contentType(MediaType.APPLICATION_JSON).content(metrics))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("M6 indisponible"));
        doThrow(new MonitoringContractException("Diagnostic incomplet")).when(client).detect(any());
        mvc.perform(post("/api/monitoring/check").contentType(MediaType.APPLICATION_JSON).content(metrics))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message").value("Diagnostic incomplet"));
    }
}
