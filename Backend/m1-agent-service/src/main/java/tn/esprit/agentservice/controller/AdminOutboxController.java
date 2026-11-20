package tn.esprit.agentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.agentservice.dto.OutboxAdminResponse;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.exception.ConflictException;
import tn.esprit.agentservice.exception.ResourceNotFoundException;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/outbox")
@Tag(name = "Administration des événements")
public class AdminOutboxController {
    private final OutboxEventRepository repository;

    public AdminOutboxController(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "Consulter l'état de la boîte d'envoi")
    @ApiResponse(responseCode = "200", description = "Comptages et échecs récents")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    public OutboxAdminResponse getStatus() {
        return new OutboxAdminResponse(
                repository.countByStatus(OutboxStatus.PENDING),
                repository.countByStatus(OutboxStatus.SENT),
                repository.countByStatus(OutboxStatus.FAILED),
                repository.findByStatusOrderByCreatedAtDesc(OutboxStatus.FAILED, PageRequest.of(0, 20))
                        .stream().map(OutboxAdminResponse.Failure::from).toList()
        );
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Replacer un événement en échec dans la file")
    @ApiResponse(responseCode = "204", description = "Événement remis en attente")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Rôle administrateur requis")
    @ApiResponse(responseCode = "404", description = "Événement introuvable")
    @ApiResponse(responseCode = "409", description = "L'événement n'est pas en échec")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        var event = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable."));
        if (event.getStatus() != OutboxStatus.FAILED) {
            throw new ConflictException("Seuls les événements en échec peuvent être relancés.");
        }
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(java.time.LocalDateTime.now());
        event.setLastError(null);
        repository.save(event);
        return ResponseEntity.noContent().build();
    }
}
