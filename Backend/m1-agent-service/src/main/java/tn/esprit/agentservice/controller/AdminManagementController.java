package tn.esprit.agentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.AdminAuditResponse;
import tn.esprit.agentservice.dto.AdminQuotaResponse;
import tn.esprit.agentservice.dto.AdminQuotaRequest;
import tn.esprit.agentservice.dto.AdminReasonRequest;
import tn.esprit.agentservice.dto.AdminUserSummary;
import tn.esprit.agentservice.dto.LlmProviderCheckResponse;
import tn.esprit.agentservice.service.AdminManagementService;
import tn.esprit.agentservice.service.LlmProviderCheckService;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin")
@Tag(name = "Administration", description = "Modération, quotas et journal administratif")
public class AdminManagementController {
    private final AdminManagementService admin;
    private final LlmProviderCheckService providerChecks;

    public AdminManagementController(AdminManagementService admin, LlmProviderCheckService providerChecks) {
        this.admin = admin;
        this.providerChecks = providerChecks;
    }

    @PostMapping("/llm/{provider}/check")
    @Operation(summary = "Vérifier la disponibilité d'un fournisseur LLM")
    @ApiResponse(responseCode = "200", description = "Résultat du test fournisseur")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    @ApiResponse(responseCode = "422", description = "Fournisseur ou modèle indisponible")
    @ApiResponse(responseCode = "429", description = "Limite de vérifications atteinte")
    public LlmProviderCheckResponse checkProvider(
            @PathVariable String provider,
            Authentication authentication
    ) {
        return providerChecks.check(provider, CurrentUser.from(authentication));
    }

    @GetMapping("/users")
    @Operation(summary = "Lister les utilisateurs connus")
    @ApiResponse(responseCode = "200", description = "Comptages, quotas et activité")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    public List<AdminUserSummary> users(Authentication authentication) {
        return admin.users(CurrentUser.from(authentication));
    }

    @PutMapping("/users/{ownerId}/quota")
    @Operation(summary = "Définir ou réinitialiser le quota d'un utilisateur")
    @ApiResponse(responseCode = "200", description = "Quota enregistré et audité")
    @ApiResponse(responseCode = "400", description = "Quota hors limites")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    public AdminQuotaResponse setQuota(
            @PathVariable String ownerId,
            @Valid @RequestBody AdminQuotaRequest request,
            Authentication authentication
    ) {
        admin.setQuota(ownerId, request.maxAgents(), CurrentUser.from(authentication));
        return new AdminQuotaResponse(ownerId, request.maxAgents());
    }

    @PostMapping("/agents/{id}/archive")
    @Operation(summary = "Archiver un agent")
    @ApiResponse(responseCode = "204", description = "Agent archivé et audité")
    @ApiResponse(responseCode = "400", description = "Raison obligatoire, 500 caractères maximum")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    public ResponseEntity<Void> archive(
            @PathVariable Long id,
            @Valid @RequestBody AdminReasonRequest request,
            Authentication authentication
    ) {
        admin.archive(id, request.reason(), CurrentUser.from(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/agents/{id}")
    @Operation(summary = "Supprimer un agent")
    @ApiResponse(responseCode = "204", description = "Agent supprimé et audité")
    @ApiResponse(responseCode = "400", description = "Raison obligatoire, 500 caractères maximum")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    @ApiResponse(responseCode = "404", description = "Agent introuvable")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam @NotBlank @Size(max = 500) String reason,
            Authentication authentication
    ) {
        admin.delete(id, reason, CurrentUser.from(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    @Operation(summary = "Consulter le journal administratif")
    @ApiResponse(responseCode = "200", description = "Entrées les plus récentes en premier")
    @ApiResponse(responseCode = "400", description = "Pagination invalide")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    public ResponseEntity<List<AdminAuditResponse>> audit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        var result = admin.auditPage(page, size, CurrentUser.from(authentication));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("X-Total-Count", Long.toString(result.getTotalElements()))
                .body(result.getContent());
    }
}
