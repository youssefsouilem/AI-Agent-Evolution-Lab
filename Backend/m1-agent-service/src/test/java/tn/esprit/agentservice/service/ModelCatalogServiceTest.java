package tn.esprit.agentservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tn.esprit.agentservice.config.LlmCatalogProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCatalogServiceTest {
    @Test
    void providerNeedsEnabledGatewayAndNonPlaceholderEnvironmentKey() {
        LlmCatalogProperties properties = new LlmCatalogProperties();
        LlmCatalogProperties.Provider provider = new LlmCatalogProperties.Provider();
        provider.setId("openai");
        provider.setLabel("OpenAI");
        provider.setEnabled(true);
        provider.setApiKeyEnv("OPENAI_API_KEY");
        LlmCatalogProperties.Model model = new LlmCatalogProperties.Model();
        model.setId("gpt-4o-mini");
        model.setLabel("GPT-4o mini");
        model.setRecommended(true);
        provider.setModels(List.of(model));
        properties.setProviders(List.of(provider));

        MockEnvironment environment = new MockEnvironment().withProperty("OPENAI_API_KEY", "missing-key");
        ModelCatalogService catalog = new ModelCatalogService(properties, environment);
        assertFalse(catalog.list().get(0).available());
        assertEquals("Clé API absente", catalog.list().get(0).reason());
        assertFalse(catalog.isModelAvailable("gpt-4o-mini"));

        environment.setProperty("OPENAI_API_KEY", "test-key-not-real");
        assertTrue(catalog.list().get(0).available());
        assertTrue(catalog.isModelAvailable("gpt-4o-mini"));
    }

    @Test
    void providerWithoutGatewayIsNotAvailableEvenWithAKey() {
        LlmCatalogProperties properties = new LlmCatalogProperties();
        LlmCatalogProperties.Provider provider = new LlmCatalogProperties.Provider();
        provider.setId("anthropic");
        provider.setLabel("Anthropic");
        provider.setEnabled(true);
        provider.setApiKeyEnv("ANTHROPIC_API_KEY");
        properties.setProviders(List.of(provider));
        ModelCatalogService catalog = new ModelCatalogService(properties,
                new MockEnvironment().withProperty("ANTHROPIC_API_KEY", "test-key-not-real"));

        assertFalse(catalog.list().get(0).available());
        assertEquals("Fournisseur non branché dans M1", catalog.list().get(0).reason());
    }
}
