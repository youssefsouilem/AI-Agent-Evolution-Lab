package tn.esprit.agentservice.dto;

import java.time.LocalDateTime;

public record AdminAuditResponse(
        Long id,
        String actorId,
        String action,
        Long targetAgentId,
        String targetOwnerId,
        String reason,
        String details,
        LocalDateTime createdAt
) {
    public static AdminAuditResponse from(tn.esprit.agentservice.entity.AdminAudit row) {
        return new AdminAuditResponse(row.getId(), row.getActorId(), row.getAction(), row.getTargetAgentId(),
                row.getTargetOwnerId(), row.getReason(), row.getDetails(), row.getCreatedAt());
    }
}
