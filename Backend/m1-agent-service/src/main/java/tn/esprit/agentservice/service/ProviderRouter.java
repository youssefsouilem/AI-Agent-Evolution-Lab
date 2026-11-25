package tn.esprit.agentservice.service;

import org.springframework.stereotype.Component;
import tn.esprit.agentservice.exception.ModelNotAvailableException;

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
        if (!request.toolCallbacks().isEmpty() && !catalog.supportsTools(providerId)) {
            throw new ModelNotAvailableException();
        }
        return gateway.call(new LlmRequest(providerId, request.model(), request.systemPrompt(),
                request.userMessage(), request.settings(), request.toolCallbacks()));
    }
}
