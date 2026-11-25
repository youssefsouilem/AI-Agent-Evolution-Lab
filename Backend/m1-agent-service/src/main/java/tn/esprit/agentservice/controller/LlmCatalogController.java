package tn.esprit.agentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.dto.LlmProviderDto;
import tn.esprit.agentservice.service.ProviderRouter;

import java.util.List;

@RestController
@RequestMapping("/api/llm/models")
@Tag(name = "LLM catalogue", description = "Fournisseurs et modèles configurés")
public class LlmCatalogController {
    private final ProviderRouter router;

    public LlmCatalogController(ProviderRouter router) {
        this.router = router;
    }

    @GetMapping
    @Operation(summary = "Lister les modèles de langage disponibles")
    @ApiResponse(responseCode = "200", description = "Catalogue sans clés API")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    public List<LlmProviderDto> list() {
        return router.listProviders();
    }
}
