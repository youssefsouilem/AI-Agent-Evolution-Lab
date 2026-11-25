package tn.esprit.agentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.MeResponse;
import tn.esprit.agentservice.repository.AgentRepository;
import tn.esprit.agentservice.service.UserQuotaService;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Current user", description = "Profil et capacités de l'utilisateur authentifié")
public class MeController {
    private final AgentRepository agents;
    private final UserQuotaService quotas;

    public MeController(AgentRepository agents, UserQuotaService quotas) {
        this.agents = agents;
        this.quotas = quotas;
    }

    @GetMapping
    @Operation(summary = "Lire le profil courant et ses capacités")
    @ApiResponse(responseCode = "200", description = "Profil sans informations secrètes")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    public MeResponse me(Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        return new MeResponse(user.id(), user.displayName(), user.role(),
                new MeResponse.Limits(quotas.effectiveLimit(user.id(), user.admin())),
                new MeResponse.Usage(agents.countByOwnerId(user.id())),
                new MeResponse.Capabilities(true, true, true, true, true, true));
    }
}
