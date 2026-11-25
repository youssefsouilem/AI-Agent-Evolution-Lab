package tn.esprit.mission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tn.esprit.mission.dto.M6DiagnosticResponse;
import tn.esprit.mission.dto.MonitoringCheckResponse;
import tn.esprit.mission.dto.MonitoringMetricsRequest;
import tn.esprit.mission.enums.AnomalySeverity;

@Service
public class MonitoringService {

    private final M6MonitoringClient client;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final AnomalySeverity mockScenario;

    public MonitoringService(
        M6MonitoringClient client,
        ObjectMapper mapper,
        @Value("${m6.enabled:false}") boolean enabled,
        @Value("${m6.mock-scenario:NORMAL}") AnomalySeverity mockScenario
    ) {
        this.client = client;
        this.mapper = mapper;
        this.enabled = enabled;
        this.mockScenario = mockScenario;
    }

    public MonitoringCheckResponse check(MonitoringMetricsRequest metrics) {
        M6DiagnosticResponse diagnostic = enabled ? client.detect(metrics) : simulatedDiagnostic();
        return new MonitoringCheckResponse(
            metrics.executionId(), enabled ? "M6" : "SIMULATION", !enabled,
            diagnostic.anomalyDetected(), diagnostic.severity(), diagnostic.reason(), diagnostic.blocking()
        );
    }

    private M6DiagnosticResponse simulatedDiagnostic() {
        var resource = new ClassPathResource("mocks/m6-diagnostics.json");
        try (var input = resource.getInputStream()) {
            var tree = mapper.readTree(input).get(mockScenario.name());
            var diagnostic = mapper.treeToValue(tree, M6DiagnosticResponse.class);
            M6MonitoringClient.validate(diagnostic);
            return diagnostic;
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de lire les scénarios M6 simulés.", exception);
        }
    }
}
