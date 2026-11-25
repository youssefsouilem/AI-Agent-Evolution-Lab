package tn.esprit.agentservice.service;

import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.agentservice.config.LlmCatalogProperties;
import tn.esprit.agentservice.dto.LlmProviderDto;
import tn.esprit.agentservice.exception.ModelAmbiguousException;
import tn.esprit.agentservice.exception.ModelNotAvailableException;
import tn.esprit.agentservice.exception.ProviderNotConfiguredException;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ModelCatalogService {
    private static final Logger logger = LoggerFactory.getLogger(ModelCatalogService.class);
    private static final Map<String, String> MODEL_MIGRATIONS = Map.of(
            "gemini-2.0-flash", "gemini-3.6-flash"
    );
    private final LlmCatalogProperties properties;
    private final Environment environment;

    public ModelCatalogService(LlmCatalogProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void logProviderAvailability() {
        properties.getProviders().forEach(provider -> logger.info(
                "LLM provider id={} enabled={} keyPresent={} modelCount={} baseUrlHost={}",
                provider.getId(), provider.isEnabled(), readKey(provider) != null,
                provider.getModels().size(), baseUrlHost(provider.getBaseUrl())));
    }

    public List<LlmProviderDto> list() {
        return properties.getProviders().stream().map(provider -> {
            String reason = unavailableReason(provider);
            return new LlmProviderDto(provider.getId(), provider.getId(), provider.getLabel(), reason == null, reason,
                    provider.isSupportsTools(), provider.getModels().stream()
                            .map(model -> new LlmProviderDto.Model(
                                    model.getId(), model.getLabel(), model.isRecommended()))
                            .toList());
        }).toList();
    }

    public String resolveProvider(String providerId, String modelId) {
        return resolve(providerId, modelId, true);
    }

    public String resolveProviderForVersion(String providerId, String modelId) {
        return resolve(providerId, modelId, false);
    }

    private String resolve(String providerId, String modelId, boolean missingKeyIsConfigurationError) {
        List<LlmCatalogProperties.Provider> configured = properties.getProviders().stream()
                .filter(provider -> provider.getModels().stream()
                        .anyMatch(model -> Objects.equals(model.getId(), modelId)
                                || Objects.equals(model.getId(), migratedModelId(provider.getId(), modelId))))
                .toList();
        if (providerId != null && !providerId.isBlank()) {
            LlmCatalogProperties.Provider selected = configured.stream()
                    .filter(provider -> providerId.equals(provider.getId())
                            || providerId.equalsIgnoreCase(provider.getLabel()))
                    .findFirst().orElseThrow(() -> new ModelNotAvailableException(modelId));
            if (!isAvailable(selected.getId())) {
                if (missingKeyIsConfigurationError && unavailableReason(selected) != null
                        && unavailableReason(selected).startsWith("Variable ")) {
                    throw new ProviderNotConfiguredException();
                }
                throw new ModelNotAvailableException(modelId);
            }
            return selected.getId();
        }
        List<LlmCatalogProperties.Provider> matching = configured.stream()
                .filter(provider -> isAvailable(provider.getId()))
                .toList();
        if (matching.isEmpty()) {
            if (missingKeyIsConfigurationError && configured.stream()
                    .anyMatch(provider -> unavailableReason(provider) != null
                            && unavailableReason(provider).startsWith("Variable "))) {
                throw new ProviderNotConfiguredException();
            }
            throw new ModelNotAvailableException(modelId);
        }
        if (matching.size() > 1) {
            throw new ModelAmbiguousException();
        }
        return matching.get(0).getId();
    }

    public String canonicalModelId(String providerId, String modelId) {
        return migratedModelId(providerId, modelId);
    }

    private String migratedModelId(String providerId, String modelId) {
        String replacement = MODEL_MIGRATIONS.get(modelId);
        if (replacement == null || !"gemini".equals(providerId)) {
            return modelId;
        }
        boolean replacementConfigured = properties.getProviders().stream()
                .filter(provider -> providerId.equals(provider.getId()))
                .flatMap(provider -> provider.getModels().stream())
                .anyMatch(model -> replacement.equals(model.getId()));
        return replacementConfigured ? replacement : modelId;
    }

    public boolean isModelAvailable(String modelId) {
        try {
            resolveProvider(null, modelId);
            return true;
        } catch (ModelNotAvailableException | ModelAmbiguousException | ProviderNotConfiguredException exception) {
            return false;
        }
    }

    public void requireAvailable(String modelId) {
        resolveProvider(null, modelId);
    }

    public void requireAvailable(String providerId, String modelId) {
        resolveProvider(providerId, modelId);
    }

    public boolean strictModelCheck() {
        return environment.getProperty("m1.llm.strict-model-check", Boolean.class, true);
    }

    public String recommendedAvailableModel() {
        return recommendedAvailableModelProvider().model();
    }

    public String recommendedAvailableModel(String providerId) {
        return recommendedAvailableModelProvider(providerId).model();
    }

    public RecommendedModel recommendedAvailableModelProvider() {
        return properties.getProviders().stream()
                .filter(provider -> isAvailable(provider.getId()))
                .filter(provider -> provider.getModels().stream()
                        .anyMatch(LlmCatalogProperties.Model::isRecommended))
                .map(provider -> new RecommendedModel(provider.getId(), provider.getModels().stream()
                        .filter(LlmCatalogProperties.Model::isRecommended)
                        .map(LlmCatalogProperties.Model::getId)
                        .findFirst().orElseThrow()))
                .findFirst()
                .orElseThrow(ProviderNotConfiguredException::new);
    }

    public RecommendedModel recommendedAvailableModelProvider(String providerId) {
        LlmCatalogProperties.Provider provider = properties.getProviders().stream()
                .filter(candidate -> providerId.equals(candidate.getId()))
                .filter(candidate -> isAvailable(candidate.getId()))
                .findFirst()
                .orElseThrow(ModelNotAvailableException::new);
        return provider.getModels().stream()
                .filter(LlmCatalogProperties.Model::isRecommended)
                .map(model -> new RecommendedModel(provider.getId(), model.getId()))
                .findFirst()
                .orElseThrow(ModelNotAvailableException::new);
    }

    public RecommendedModel recommendedModel(String providerId) {
        LlmCatalogProperties.Provider provider = properties.getProviders().stream()
                .filter(candidate -> providerId.equals(candidate.getId()))
                .findFirst().orElseThrow(ModelNotAvailableException::new);
        return provider.getModels().stream()
                .filter(LlmCatalogProperties.Model::isRecommended)
                .map(model -> new RecommendedModel(provider.getId(), model.getId()))
                .findFirst()
                .orElseThrow(ModelNotAvailableException::new);
    }

    public boolean hasAnyAvailableProvider() {
        return list().stream().anyMatch(LlmProviderDto::available);
    }

    public boolean isAvailable(String providerId) {
        return properties.getProviders().stream()
                .filter(provider -> providerId.equals(provider.getId()))
                .anyMatch(provider -> unavailableReason(provider) == null);
    }

    public boolean supportsTools(String providerId) {
        return properties.getProviders().stream()
                .filter(provider -> providerId.equals(provider.getId()))
                .findFirst().map(LlmCatalogProperties.Provider::isSupportsTools).orElse(false);
    }

    public String apiKey(String providerId) {
        LlmCatalogProperties.Provider provider = provider(providerId);
        String key = readKey(provider);
        if (key == null) {
            throw new ProviderNotConfiguredException();
        }
        return key;
    }

    public String baseUrl(String providerId) {
        return provider(providerId).getBaseUrl();
    }

    private LlmCatalogProperties.Provider provider(String providerId) {
        return properties.getProviders().stream()
                .filter(provider -> providerId.equals(provider.getId()))
                .findFirst().orElseThrow(ModelNotAvailableException::new);
    }

    private String unavailableReason(LlmCatalogProperties.Provider provider) {
        if (!provider.isEnabled()) {
            return "Fournisseur désactivé";
        }
        if (provider.getModels().isEmpty()) {
            return "Aucun modèle configuré pour ce fournisseur";
        }
        if (readKey(provider) == null) {
            return provider.getApiKeyEnv() == null || provider.getApiKeyEnv().isBlank()
                    ? "Fournisseur non branché dans M1"
                    : "Variable " + provider.getApiKeyEnv() + " absente ou vide dans le processus de M1";
        }
        if (!hasBaseUrl(provider)) {
            return "Fournisseur non branché dans M1";
        }
        return null;
    }

    private String readKey(LlmCatalogProperties.Provider provider) {
        if (provider.getApiKeyEnv() == null || provider.getApiKeyEnv().isBlank()) {
            return null;
        }
        String key = environment.getProperty(provider.getApiKeyEnv());
        if (key == null) {
            return null;
        }
        key = key.trim();
        while (!key.isEmpty() && isQuote(key.charAt(0))) {
            key = key.substring(1).trim();
        }
        while (!key.isEmpty() && isQuote(key.charAt(key.length() - 1))) {
            key = key.substring(0, key.length() - 1).trim();
        }
        return key.isBlank() ? null : key;
    }

    private boolean isQuote(char character) {
        return character == '"' || character == '\'';
    }

    private boolean hasBaseUrl(LlmCatalogProperties.Provider provider) {
        return provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank();
    }

    private String baseUrlHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "none";
        }
        try {
            String host = URI.create(baseUrl).getHost();
            return host == null ? "invalid" : host;
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }

    public record RecommendedModel(String provider, String model) { }
}
