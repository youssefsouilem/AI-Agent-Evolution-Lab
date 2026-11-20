package tn.esprit.agentservice.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.ExecutionDetail;
import tn.esprit.agentservice.dto.ExecutionSummary;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.service.AgentExecutionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@Validated
@Tag(name = "Execution history", description = "Historique des exécutions d'agents")
public class ExecutionHistoryController {
    private final AgentExecutionHistoryService history;

    public ExecutionHistoryController(AgentExecutionHistoryService history) {
        this.history = history;
    }

    @GetMapping("/api/agents/{id}/executions")
    @Operation(summary = "Lister l'historique d'un agent")
    @ApiResponse(responseCode = "200", description = "Tableau des exécutions avec X-Total-Count")
    @ApiResponse(responseCode = "400", description = "Filtres ou pagination invalides")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    public ResponseEntity<List<ExecutionSummary>> byAgent(
            @PathVariable Long id,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(required = false) Long versionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        var result = history.forAgent(id, status, versionId, page, size, CurrentUser.from(authentication));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("X-Total-Count", Long.toString(result.total())).body(result.items());
    }

    @GetMapping("/api/agents/{id}/executions/{executionId}")
    @Operation(summary = "Lire le détail d'une exécution")
    @ApiResponse(responseCode = "200", description = "Détail d'exécution")
    @ApiResponse(responseCode = "404", description = "Agent ou exécution introuvable")
    public ExecutionDetail detail(
            @PathVariable Long id,
            @PathVariable Long executionId,
            Authentication authentication
    ) {
        return history.detail(id, executionId, CurrentUser.from(authentication));
    }

    @GetMapping("/api/executions")
    @Operation(summary = "Lister les exécutions visibles tous agents confondus")
    @ApiResponse(responseCode = "200", description = "Tableau des exécutions avec X-Total-Count")
    @ApiResponse(responseCode = "400", description = "Filtres ou pagination invalides")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    public ResponseEntity<List<ExecutionSummary>> acrossAgents(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "false") boolean all,
            Authentication authentication
    ) {
        var result = history.acrossAgents(agentId, status, page, size, all, CurrentUser.from(authentication));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total())).body(result.items());
    }
}
