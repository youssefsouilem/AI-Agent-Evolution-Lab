package tn.esprit.mission;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

class ConfigClientBootstrapTest {
    @Configuration(proxyBeanMethods = false)
    static class Probe {}

    @Test
    void enabledClientLoadsPropertiesFromConfiguredServer() throws Exception {
        checkClient(true);
    }

    @Test
    void disabledClientMakesNoRequestToConfigServer() throws Exception {
        checkClient(false);
    }

    private void checkClient(boolean enabled) throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/m3-mission-service/", exchange -> {
            requests.incrementAndGet();
            byte[] body = """
                {"name":"m3-mission-service","profiles":["default"],"label":"main",
                 "propertySources":[{"name":"test-repository/m3-mission-service.properties",
                 "source":{"m3.config.probe":"loaded-from-config-server"}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var app = new SpringApplication(Probe.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setRegisterShutdownHook(false);
            try (var context = app.run(
                "--spring.config.location=classpath:application.properties",
                "--spring.main.banner-mode=off",
                "--spring.cloud.config.enabled=" + enabled,
                "--CONFIG_SERVER_URL=http://127.0.0.1:" + server.getAddress().getPort(),
                "--CONFIG_PROFILE=default", "--CONFIG_LABEL=main",
                "--eureka.client.enabled=false")) {
                if (enabled) {
                    assertEquals("loaded-from-config-server", context.getEnvironment().getProperty("m3.config.probe"));
                    assertTrue(requests.get() > 0);
                } else {
                    assertNull(context.getEnvironment().getProperty("m3.config.probe"));
                    assertEquals(0, requests.get());
                }
            }
        } finally { server.stop(0); }
    }
}
