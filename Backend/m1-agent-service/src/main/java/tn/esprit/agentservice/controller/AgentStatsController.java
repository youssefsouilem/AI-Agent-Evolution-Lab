package tn.esprit.agentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.AgentStats;
import tn.esprit.agentservice.service.AgentStatsService;

@RestController
@Validated
@RequestMapping("/api/agents/{id}/stats")
@Tag(name = "Agent statistics", description = "Statistiques agrégées des exécutions")
public class AgentStatsController {
    private final AgentStatsService stats;

    public AgentStatsController(AgentStatsService stats) {
        this.stats = stats;
    }

    @GetMapping
    @Operation(summary = "Consulter les statistiques d'exécution")
    @ApiResponse(responseCode = "200", description = "Statistiques agrégées")
    @ApiResponse(responseCode = "400", description = "Période invalide")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    public AgentStats get(
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
            Authentication authentication
    ) {
        return stats.get(id, days, CurrentUser.from(authentication));
    }
}
