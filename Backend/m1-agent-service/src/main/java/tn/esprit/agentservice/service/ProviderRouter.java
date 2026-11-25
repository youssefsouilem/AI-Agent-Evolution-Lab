package tn.esprit.agentservice.service;

import org.springframework.stereotype.Component;
import tn.esprit.agentservice.dto.LlmProviderDto;
import tn.esprit.agentservice.exception.ModelNotAvailableException;

import java.util.List;

@Component
public class ProviderRouter {
    private final LlmGateway gateway;
    private final ModelCatalogService catalog;

    public ProviderRouter(LlmGateway gateway, ModelCatalogService catalog) {
        this.gateway = gateway;
        this.catalog = catalog;
    }

    public LlmResult call(LlmRequest request) {
        String providerId = catalog.resolveProvider(request.provider(), request.model());
        String modelId = catalog.canonicalModelId(providerId, request.model());
        if (!request.toolCallbacks().isEmpty() && !catalog.supportsTools(providerId)) {
            throw new ModelNotAvailableException();
        }
        return gateway.call(new LlmRequest(providerId, modelId, request.systemPrompt(),
                request.userMessage(), request.settings(), request.toolCallbacks()));
    }

    public List<LlmProviderDto> listProviders() {
        return catalog.list();
    }
}
