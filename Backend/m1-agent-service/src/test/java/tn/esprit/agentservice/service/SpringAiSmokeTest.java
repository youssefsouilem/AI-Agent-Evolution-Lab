package tn.esprit.agentservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SpringAiSmokeTest {
    @Autowired private LlmGateway gateway;

    @Test
    void makesOneTinyRealProviderCall() {
        long start = System.nanoTime();
        LlmResult result = gateway.call(new LlmRequest("gpt-4o-mini", "Réponds uniquement en français.",
                "Réponds par le mot OK.", new LlmSettings(0, 16, 20), List.of()));
        long durationMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertNotNull(result.text());
        assertTrue(!result.text().isBlank());
        assertNotNull(result.totalTokens());
        assertTrue(durationMs > 0);
    }
}
