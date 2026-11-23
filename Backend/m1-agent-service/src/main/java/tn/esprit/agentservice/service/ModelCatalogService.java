package tn.esprit.agentservice.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tn.esprit.agentservice.config.LlmCatalogProperties;
import tn.esprit.agentservice.dto.LlmProviderDto;
import tn.esprit.agentservice.exception.ModelNotAvailableException;

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
            boolean wired = "openai".equalsIgnoreCase(provider.getId());
            boolean keySet = hasKey(provider);
            boolean available = provider.isEnabled() && keySet && wired;
            String reason = available ? null
                    : !keySet ? "Clé API absente" : "Fournisseur non branché dans M1";
            return new LlmProviderDto(provider.getId(), provider.getLabel(), available, reason,
                    provider.getModels().stream()
                            .map(model -> new LlmProviderDto.Model(
                                    model.getId(), model.getLabel(), model.isRecommended()))
                            .toList());
        }).toList();
    }

    public boolean isModelAvailable(String modelId) {
        return properties.getProviders().stream()
                .filter(provider -> provider.isEnabled())
                .filter(provider -> hasKey(provider))
                .filter(provider -> "openai".equalsIgnoreCase(provider.getId()))
                .flatMap(provider -> provider.getModels().stream())
                .anyMatch(model -> Objects.equals(model.getId(), modelId));
    }

    public void requireAvailable(String modelId) {
        if (!isModelAvailable(modelId)) {
            throw new ModelNotAvailableException();
        }
    }

    public boolean strictModelCheck() {
        return environment.getProperty("m1.llm.strict-model-check", Boolean.class, true);
    }

    public String recommendedAvailableModel() {
        return properties.getProviders().stream()
                .filter(provider -> provider.isEnabled() && hasKey(provider)
                        && "openai".equalsIgnoreCase(provider.getId()))
                .flatMap(provider -> provider.getModels().stream())
                .filter(LlmCatalogProperties.Model::isRecommended)
                .map(LlmCatalogProperties.Model::getId)
                .findFirst()
                .orElseThrow(tn.esprit.agentservice.exception.ProviderNotConfiguredException::new);
    }

    public String providerForAvailableModel(String modelId) {
        return properties.getProviders().stream()
                .filter(provider -> provider.isEnabled() && hasKey(provider)
                        && "openai".equalsIgnoreCase(provider.getId()))
                .filter(provider -> provider.getModels().stream()
                        .anyMatch(model -> Objects.equals(model.getId(), modelId)))
                .map(LlmCatalogProperties.Provider::getId)
                .findFirst()
                .orElseThrow(ModelNotAvailableException::new);
    }

    public boolean hasAnyAvailableProvider() {
        return list().stream().anyMatch(LlmProviderDto::available);
    }

    private boolean hasKey(LlmCatalogProperties.Provider provider) {
        if (provider.getApiKeyEnv() == null || provider.getApiKeyEnv().isBlank()) {
            return false;
        }
        String key = environment.getProperty(provider.getApiKeyEnv());
        return key != null && !key.isBlank() && !"missing-key".equalsIgnoreCase(key.trim());
    }
}
