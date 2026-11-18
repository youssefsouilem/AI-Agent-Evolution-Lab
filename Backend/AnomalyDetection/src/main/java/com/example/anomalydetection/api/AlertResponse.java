package com.example.anomalydetection.api;

import com.example.anomalydetection.domain.Alert;
import java.time.LocalDateTime;

public record AlertResponse(Long id, String severity, String message, String status, Long anomalyCheckId,
                            LocalDateTime createdAt, LocalDateTime acknowledgedAt, LocalDateTime resolvedAt) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(a.getId(), a.getSeverity().name(), a.getMessage(), a.getStatus().name(),
                a.getAnomalyCheck().getId(), a.getCreatedAt(), a.getAcknowledgedAt(), a.getResolvedAt());
    }
}
