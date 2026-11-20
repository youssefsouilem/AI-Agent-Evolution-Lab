package tn.esprit.agentservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.ConfigurationDto;
import tn.esprit.agentservice.dto.TunableParameterDto;
import tn.esprit.agentservice.dto.VersionRequest;
import tn.esprit.agentservice.dto.VersionResponse;
import tn.esprit.agentservice.service.AgentVersionService;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/agents/{id}/versions")
@Tag(name = "Agent versions", description = "Gestion des versions et paramètres ajustables")
public class AgentVersionController {

    private final AgentVersionService versionService;

    public AgentVersionController(AgentVersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une version")
    @ApiResponse(responseCode = "201", description = "Version créée")
    @ApiResponse(responseCode = "400", description = "Corps invalide")
    @ApiResponse(responseCode = "404", description = "Agent ou version de référence introuvable")
    @ApiResponse(responseCode = "422", description = "Paramètres invalides")
    public VersionResponse create(
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request,
            Authentication authentication
    ) {
        return versionService.create(id, request, CurrentUser.from(authentication));
    }

    @GetMapping
    @Operation(summary = "Lister les versions d'un agent")
    @ApiResponse(responseCode = "200", description = "Liste des versions")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    public List<VersionResponse> list(@PathVariable Long id, Authentication authentication) {
        return versionService.list(id, CurrentUser.from(authentication));
    }

    @GetMapping("/{vid}")
    @Operation(summary = "Lire une version")
    @ApiResponse(responseCode = "200", description = "Version")
    @ApiResponse(responseCode = "404", description = "Agent ou version introuvable")
    public VersionResponse get(
            @PathVariable Long id,
            @PathVariable Long vid,
            Authentication authentication
    ) {
        return versionService.get(id, vid, CurrentUser.from(authentication));
    }

    @GetMapping("/{vid}/tunable-parameters")
    @Operation(summary = "Lister les paramètres ajustables")
    @ApiResponse(responseCode = "200", description = "Paramètres ajustables")
    @ApiResponse(responseCode = "404", description = "Agent ou version introuvable")
    public List<TunableParameterDto> tunableParameters(
            @PathVariable Long id,
            @PathVariable Long vid,
            Authentication authentication
    ) {
        return versionService.tunableParameters(id, vid, CurrentUser.from(authentication));
    }

    @PutMapping("/{vid}/activate")
    @Operation(summary = "Activer une version")
    @ApiResponse(responseCode = "200", description = "Version activée")
    @ApiResponse(responseCode = "404", description = "Agent ou version introuvable")
    public VersionResponse activate(
            @PathVariable Long id,
            @PathVariable Long vid,
            Authentication authentication
    ) {
        return versionService.activate(id, vid, CurrentUser.from(authentication));
    }

    @PutMapping("/{vid}/configuration")
    @Operation(summary = "Mettre à jour la configuration d'une version")
    @ApiResponse(responseCode = "200", description = "Configuration mise à jour")
    @ApiResponse(responseCode = "404", description = "Agent ou version introuvable")
    @ApiResponse(responseCode = "422", description = "Configuration invalide")
    public VersionResponse updateConfiguration(
            @PathVariable Long id,
            @PathVariable Long vid,
            @RequestBody ConfigurationDto configuration,
            Authentication authentication
    ) {
        return versionService.updateConfiguration(id, vid, configuration, CurrentUser.from(authentication));
    }
}
