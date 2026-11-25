package tn.esprit.agentservice.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import tn.esprit.agentservice.config.LlmCatalogProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCatalogServiceTest {
    @Test
    void providerAvailabilityUsesTrimmedRuntimeKeyAndPreciseReason() {
        LlmCatalogProperties properties = new LlmCatalogProperties();
        LlmCatalogProperties.Provider provider = new LlmCatalogProperties.Provider();
        provider.setId("openai");
        provider.setLabel("OpenAI");
        provider.setEnabled(true);
        provider.setApiKeyEnv("OPENAI_API_KEY");
        provider.setBaseUrl("https://api.openai.com/v1");
        LlmCatalogProperties.Model model = new LlmCatalogProperties.Model();
        model.setId("gpt-4o-mini");
        model.setLabel("GPT-4o mini");
        model.setRecommended(true);
        provider.setModels(List.of(model));
        properties.setProviders(List.of(provider));

        MockEnvironment environment = new MockEnvironment().withProperty("OPENAI_API_KEY", "  '  \"  '  ");
        ModelCatalogService catalog = new ModelCatalogService(properties, environment);
        assertFalse(catalog.list().get(0).available());
        assertEquals("Variable OPENAI_API_KEY absente ou vide dans le processus de M1",
                catalog.list().get(0).reason());
        assertFalse(catalog.isModelAvailable("gpt-4o-mini"));

        environment.setProperty("OPENAI_API_KEY", "  '\"runtime-test-token\"'  ");
        assertTrue(catalog.list().get(0).available());
        assertTrue(catalog.isModelAvailable("gpt-4o-mini"));
        assertEquals("runtime-test-token", catalog.apiKey("openai"));

        environment.setProperty("OPENAI_API_KEY", "  \"runtime-test-token-updated\"  ");
        assertEquals("runtime-test-token-updated", catalog.apiKey("openai"));
    }

    @Test
    void providerWithoutGatewayIsNotAvailableEvenWithAKey() {
        LlmCatalogProperties properties = new LlmCatalogProperties();
        LlmCatalogProperties.Provider provider = new LlmCatalogProperties.Provider();
        provider.setId("anthropic");
        provider.setLabel("Anthropic");
        provider.setEnabled(true);
        provider.setApiKeyEnv("ANTHROPIC_API_KEY");
        provider.setBaseUrl(null);
        LlmCatalogProperties.Model model = new LlmCatalogProperties.Model();
        model.setId("anthropic-model");
        model.setLabel("Anthropic model");
        model.setRecommended(true);
        provider.setModels(List.of(model));
        properties.setProviders(List.of(provider));
        ModelCatalogService catalog = new ModelCatalogService(properties,
                new MockEnvironment().withProperty("ANTHROPIC_API_KEY", "configured-test-token"));

        assertFalse(catalog.list().get(0).available());
        assertEquals("Fournisseur non branché dans M1", catalog.list().get(0).reason());
    }

    @Test
    void disabledProviderAndProviderWithoutModelsHaveSpecificReasons() {
        LlmCatalogProperties.Provider disabled = provider("disabled", true, "DISABLED_API_KEY");
        disabled.setEnabled(false);
        LlmCatalogProperties.Provider noModels = provider("empty", true, "EMPTY_API_KEY");
        noModels.setModels(List.of());
        LlmCatalogProperties properties = new LlmCatalogProperties();
        properties.setProviders(List.of(disabled, noModels));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DISABLED_API_KEY", "configured-test-token")
                .withProperty("EMPTY_API_KEY", "configured-test-token");
        ModelCatalogService catalog = new ModelCatalogService(properties, environment);

        assertEquals("Fournisseur désactivé", catalog.list().get(0).reason());
        assertEquals("Aucun modèle configuré pour ce fournisseur", catalog.list().get(1).reason());
    }

    @Test
    void retiredGeminiModelResolvesToItsConfiguredSuccessorAndProviderLabelsAreAccepted() {
        LlmCatalogProperties.Provider gemini = provider("gemini", true, "GEMINI_API_KEY");
        gemini.setLabel("Google Gemini");
        LlmCatalogProperties.Model replacement = new LlmCatalogProperties.Model();
        replacement.setId("gemini-3.6-flash");
        replacement.setLabel("Gemini 3.6 Flash");
        replacement.setRecommended(true);
        gemini.setModels(List.of(replacement));
        LlmCatalogProperties properties = new LlmCatalogProperties();
        properties.setProviders(List.of(gemini));
        ModelCatalogService catalog = new ModelCatalogService(properties,
                new MockEnvironment().withProperty("GEMINI_API_KEY", "configured-test-token"));

        assertEquals("gemini", catalog.resolveProvider("Google Gemini", "gemini-2.0-flash"));
        assertEquals("gemini-3.6-flash", catalog.canonicalModelId("gemini", "gemini-2.0-flash"));
    }

    @Test
    void startupDiagnosticContainsOnlyProviderAvailabilityMetadata() {
        LlmCatalogProperties.Provider provider = provider("openai", true, "OPENAI_API_KEY");
        LlmCatalogProperties properties = new LlmCatalogProperties();
        properties.setProviders(List.of(provider));
        ModelCatalogService catalog = new ModelCatalogService(properties,
                new MockEnvironment().withProperty("OPENAI_API_KEY", "runtime-test-token"));

        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(ModelCatalogService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            catalog.logProviderAvailability();
        } finally {
            logger.detachAppender(logs);
        }

        assertEquals(1, logs.list.size());
        String message = logs.list.get(0).getFormattedMessage();
        assertTrue(message.contains("id=openai enabled=true keyPresent=true modelCount=1"));
        assertTrue(message.contains("baseUrlHost=example.test"));
        assertFalse(message.contains("runtime-test-token"));
    }

    private LlmCatalogProperties.Provider provider(String id, boolean enabled, String keyEnv) {
        LlmCatalogProperties.Provider provider = new LlmCatalogProperties.Provider();
        provider.setId(id);
        provider.setLabel(id);
        provider.setEnabled(enabled);
        provider.setApiKeyEnv(keyEnv);
        provider.setBaseUrl("https://example.test/v1");
        LlmCatalogProperties.Model model = new LlmCatalogProperties.Model();
        model.setId(id + "-model");
        model.setLabel(id + " model");
        model.setRecommended(true);
        provider.setModels(List.of(model));
        return provider;
    }
}
