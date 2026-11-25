package tn.esprit.agentservice.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tn.esprit.agentservice.config.LlmCatalogProperties;
import tn.esprit.agentservice.dto.LlmProviderDto;
import tn.esprit.agentservice.exception.ModelAmbiguousException;
import tn.esprit.agentservice.exception.ModelNotAvailableException;
import tn.esprit.agentservice.exception.ProviderNotConfiguredException;

import java.util.List;
import java.util.Objects;

@Service
public class ModelCatalogService {
    private final LlmCatalogProperties properties;
    private final Environment environment;

    public ModelCatalogService(LlmCatalogProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public List<LlmProviderDto> list() {
        return properties.getProviders().stream().map(provider -> {
            boolean keySet = hasKey(provider);
            boolean wired = provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank();
            boolean available = provider.isEnabled() && keySet && wired;
            String reason = available ? null
                    : !keySet ? "Clé API absente" : "Fournisseur non branché dans M1";
            return new LlmProviderDto(provider.getId(), provider.getId(), provider.getLabel(), available, reason,
                    provider.isSupportsTools(), provider.getModels().stream()
                            .map(model -> new LlmProviderDto.Model(
                                    model.getId(), model.getLabel(), model.isRecommended()))
                            .toList());
        }).toList();
    }

    public String resolveProvider(String providerId, String modelId) {
        List<LlmCatalogProperties.Provider> matching = properties.getProviders().stream()
                .filter(provider -> provider.isEnabled() && hasKey(provider) && hasBaseUrl(provider))
                .filter(provider -> provider.getModels().stream()
                        .anyMatch(model -> Objects.equals(model.getId(), modelId)))
                .toList();
        if (providerId != null && !providerId.isBlank()) {
            return matching.stream()
                    .filter(provider -> providerId.equals(provider.getId()))
                    .map(LlmCatalogProperties.Provider::getId)
                    .findFirst()
                    .orElseThrow(ModelNotAvailableException::new);
        }
        if (matching.isEmpty()) {
            throw new ModelNotAvailableException();
        }
        if (matching.size() > 1) {
            throw new ModelAmbiguousException();
        }
        return matching.get(0).getId();
    }

    public boolean isModelAvailable(String modelId) {
        try {
            resolveProvider(null, modelId);
            return true;
        } catch (ModelNotAvailableException | ModelAmbiguousException exception) {
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
                .anyMatch(provider -> provider.isEnabled() && hasKey(provider) && hasBaseUrl(provider));
    }

    public boolean supportsTools(String providerId) {
        return properties.getProviders().stream()
                .filter(provider -> providerId.equals(provider.getId()))
                .findFirst().map(LlmCatalogProperties.Provider::isSupportsTools).orElse(false);
    }

    public String apiKey(String providerId) {
        LlmCatalogProperties.Provider provider = provider(providerId);
        String key = provider.getApiKeyEnv() == null ? null : environment.getProperty(provider.getApiKeyEnv());
        if (key == null || key.isBlank() || "missing-key".equalsIgnoreCase(key.trim())) {
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

    private boolean hasBaseUrl(LlmCatalogProperties.Provider provider) {
        return provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank();
    }

    private boolean hasKey(LlmCatalogProperties.Provider provider) {
        if (provider.getApiKeyEnv() == null || provider.getApiKeyEnv().isBlank()) {
            return false;
        }
        String key = environment.getProperty(provider.getApiKeyEnv());
        return key != null && !key.isBlank() && !"missing-key".equalsIgnoreCase(key.trim());
    }

    public record RecommendedModel(String provider, String model) { }
}
