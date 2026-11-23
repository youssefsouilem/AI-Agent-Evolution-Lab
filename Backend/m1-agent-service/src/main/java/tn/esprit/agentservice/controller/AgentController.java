package tn.esprit.agentservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.AgentRequest;
import tn.esprit.agentservice.dto.AgentResponse;
import tn.esprit.agentservice.dto.ExecuteRequest;
import tn.esprit.agentservice.dto.ExecuteResponse;
import tn.esprit.agentservice.service.AgentExecutionService;
import tn.esprit.agentservice.service.AgentService;

import java.net.URI;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/agents")
@Tag(name = "Agents", description = "Gestion des agents")
public class AgentController {

    private final AgentService agentService;
    private final AgentExecutionService executionService;

    public AgentController(AgentService agentService, AgentExecutionService executionService) {
        this.agentService = agentService;
        this.executionService = executionService;
    }

    @PostMapping
    @Operation(summary = "Créer un agent")
    @ApiResponse(responseCode = "201", description = "Agent créé")
    @ApiResponse(responseCode = "400", description = "Corps invalide")
    @ApiResponse(responseCode = "403", description = "Quota utilisateur atteint")
    public ResponseEntity<AgentResponse> create(
            @Valid @RequestBody AgentRequest request,
            Authentication authentication
    ) {
        AgentResponse response = agentService.create(request, CurrentUser.from(authentication));
        return ResponseEntity.created(URI.create("/api/agents/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lister les agents visibles")
    @ApiResponse(responseCode = "200", description = "Liste des agents")
    public List<AgentResponse> list(
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String ownerId,
            Authentication authentication
    ) {
        return agentService.list(CurrentUser.from(authentication), all, ownerId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire un agent")
    @ApiResponse(responseCode = "200", description = "Agent")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    public AgentResponse get(@PathVariable Long id, Authentication authentication) {
        return agentService.get(id, CurrentUser.from(authentication));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un agent")
    @ApiResponse(responseCode = "200", description = "Agent modifié")
    @ApiResponse(responseCode = "400", description = "Corps invalide")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    @ApiResponse(responseCode = "403", description = "Administrateur en lecture seule sur cet agent")
    public AgentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AgentRequest request,
            Authentication authentication
    ) {
        return agentService.update(id, request, CurrentUser.from(authentication));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un agent")
    @ApiResponse(responseCode = "204", description = "Agent supprimé")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    @ApiResponse(responseCode = "403", description = "Administrateur en lecture seule sur cet agent")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        agentService.delete(id, CurrentUser.from(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "Exécuter une version d'agent")
    @ApiResponse(responseCode = "200", description = "Exécution terminée")
    @ApiResponse(responseCode = "400", description = "Corps invalide")
    @ApiResponse(responseCode = "404", description = "Agent ou version introuvable")
    @ApiResponse(responseCode = "422", description = "Configuration invalide ou modèle indisponible")
    @ApiResponse(responseCode = "502", description = "Échec du fournisseur ou des outils")
    @ApiResponse(responseCode = "504", description = "Délai d'exécution dépassé")
    @ApiResponse(responseCode = "403", description = "Administrateur en lecture seule sur cet agent")
    public ExecuteResponse execute(
            @PathVariable Long id,
            @Valid @RequestBody ExecuteRequest request,
            Authentication authentication
    ) {
        return executionService.execute(id, request, CurrentUser.from(authentication));
    }
}
