package tn.esprit.agentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.AgentAssistRequest;
import tn.esprit.agentservice.dto.AgentProposal;
import tn.esprit.agentservice.service.AgentAssistService;

@RestController
@RequestMapping("/api/agents/assist")
@Tag(name = "Agent assistant", description = "Génération d'une proposition non persistée")
public class AgentAssistController {
    private final AgentAssistService assist;

    public AgentAssistController(AgentAssistService assist) {
        this.assist = assist;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Proposer la configuration d'un agent")
    @ApiResponse(responseCode = "200", description = "Proposition à réviser, jamais enregistrée")
    @ApiResponse(responseCode = "400", description = "Description invalide")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "422", description = "Fournisseur ou modèle indisponible")
    @ApiResponse(responseCode = "429", description = "Quota horaire atteint")
    @ApiResponse(responseCode = "502", description = "Fournisseur absent ou sortie invalide")
    public AgentProposal propose(
            @Valid @RequestBody AgentAssistRequest request,
            Authentication authentication
    ) {
        return assist.propose(request, CurrentUser.from(authentication));
    }
}
