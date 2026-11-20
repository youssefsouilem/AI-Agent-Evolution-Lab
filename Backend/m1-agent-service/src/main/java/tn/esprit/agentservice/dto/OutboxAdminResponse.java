package tn.esprit.agentservice.dto;

import tn.esprit.agentservice.entity.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OutboxAdminResponse(long pending, long sent, long failed, List<Failure> latestFailures) {
    public record Failure(UUID id, String eventType, LocalDateTime createdAt, int attempts, String lastError) {
        public static Failure from(OutboxEvent event) {
            return new Failure(event.getId(), event.getEventType(), event.getCreatedAt(),
                    event.getAttempts(), event.getLastError());
        }
    }
}
