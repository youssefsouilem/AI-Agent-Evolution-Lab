package tn.esprit.mission;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tn.esprit.mission.exception.AgentCatalogUnavailableException;
import tn.esprit.mission.service.M1AgentClient;

import static org.junit.jupiter.api.Assertions.*;

class M1AgentClientTest {
    @Test
    void retrievesCatalogueAndSeparateVersions() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/agents", exchange -> {
            String body = exchange.getRequestURI().getPath().endsWith("/versions")
                ? "[{\"id\":421,\"versionNumber\":2,\"active\":true,\"model\":\"test\"}]"
                : "[{\"id\":42,\"name\":\"Agent M1\",\"status\":\"ACTIVE\"}]";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var client = client(server);
            var agents = client.getAgents();
            assertEquals(42L, agents.get(0).id());
            assertEquals(421L, agents.get(0).versions().get(0).id());
        } finally { server.stop(0); }
    }

    @Test
    void upstreamFailureDoesNotReturnMockAgents() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/agents", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            assertThrows(AgentCatalogUnavailableException.class, () -> client(server).getAgents());
        } finally { server.stop(0); }
    }

    private M1AgentClient client(HttpServer server) {
        return new M1AgentClient(RestClient.builder(),
            "http://127.0.0.1:" + server.getAddress().getPort(), 1000, 1000);
    }
}
