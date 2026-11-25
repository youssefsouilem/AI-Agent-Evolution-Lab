package tn.esprit.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tn.esprit.mission.dto.MonitoringMetricsRequest;
import tn.esprit.mission.enums.AnomalySeverity;
import tn.esprit.mission.exception.MonitoringContractException;
import tn.esprit.mission.exception.MonitoringUnavailableException;
import tn.esprit.mission.service.M6MonitoringClient;
import tn.esprit.mission.service.MonitoringService;

import static org.junit.jupiter.api.Assertions.*;

class M6MonitoringClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MonitoringMetricsRequest metrics = new MonitoringMetricsRequest(31L, 2400L, 850L, 2, "COMPLETED");

    @Test
    void sendsMeasuredMetricsAndPreservesRealBlockingDecision() throws Exception {
        var received = new AtomicReference<String>();
        var method = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/custom/detect", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            method.set(exchange.getRequestMethod());
            byte[] body = """
                {"anomalyDetected":true,"severity":"CRITICAL","reason":"EXECUTION_FAILURE","blocking":true}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var service = new MonitoringService(client(server, "/custom/detect", 1000), mapper, true, AnomalySeverity.NORMAL);
            var result = service.check(metrics);
            assertEquals("POST", method.get());
            assertEquals(mapper.readTree(mapper.writeValueAsString(metrics)), mapper.readTree(received.get()));
            assertEquals("M6", result.source());
            assertFalse(result.simulated());
            assertTrue(result.blocking());
            assertEquals(AnomalySeverity.CRITICAL, result.severity());
            assertEquals("EXECUTION_FAILURE", result.reason());
        } finally { server.stop(0); }
    }

    @Test
    void upstreamFailureNeverFallsBackToNormalFixture() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/anomalies/detect", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            var service = new MonitoringService(client(server, "/api/anomalies/detect", 1000), mapper, true, AnomalySeverity.NORMAL);
            assertThrows(MonitoringUnavailableException.class, () -> service.check(metrics));
        } finally { server.stop(0); }
    }

    @Test
    void missingBlockingOrUnknownSeverityIsRejected() throws Exception {
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/anomalies/detect", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var client = client(server, "/api/anomalies/detect", 1000);
            for (String invalid : new String[] {
                "{\"anomalyDetected\":false,\"severity\":\"NORMAL\"}",
                "{\"anomalyDetected\":false,\"severity\":\"UNKNOWN\",\"blocking\":false}",
                "{}", "invalid-json"
            }) {
                body.set(invalid);
                assertThrows(MonitoringContractException.class, () -> client.detect(metrics));
            }
        } finally { server.stop(0); }
    }

    @Test
    void slowServiceTimesOutWithoutInventingDiagnosis() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/anomalies/detect", exchange -> {
            try { Thread.sleep(500); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            assertThrows(MonitoringUnavailableException.class,
                () -> client(server, "/api/anomalies/detect", 50).detect(metrics));
        } finally { server.stop(0); }
    }

    private M6MonitoringClient client(HttpServer server, String path, int readTimeout) {
        return new M6MonitoringClient(RestClient.builder(),
            "http://127.0.0.1:" + server.getAddress().getPort(), path, 1000, readTimeout);
    }
}
