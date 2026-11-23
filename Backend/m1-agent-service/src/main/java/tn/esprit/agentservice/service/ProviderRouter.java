package tn.esprit.agentservice.service;

import org.springframework.stereotype.Component;
import tn.esprit.agentservice.exception.ModelNotAvailableException;

@Component
public class ProviderRouter {
    private final LlmGateway openAiGateway;
    private final ModelCatalogService catalog;

    public ProviderRouter(LlmGateway openAiGateway, ModelCatalogService catalog) {
        this.openAiGateway = openAiGateway;
        this.catalog = catalog;
    }

    public LlmResult call(LlmRequest request) {
        String provider = catalog.providerForAvailableModel(request.model());
        if ("openai".equalsIgnoreCase(provider)) {
            return openAiGateway.call(request);
        }
        throw new ModelNotAvailableException();
    }
}
