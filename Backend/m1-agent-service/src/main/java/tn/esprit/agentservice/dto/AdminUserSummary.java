package tn.esprit.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminUserSummary(
        String ownerId,
        long agentCount,
        long activeAgentCount,
        Integer effectiveQuota,
        boolean quotaIsOverride,
        LocalDateTime lastActivityAt
) { }
