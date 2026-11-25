package tn.esprit.mission.dto;

import tn.esprit.mission.enums.AnomalySeverity;

public record M6DiagnosticResponse(
    Boolean anomalyDetected,
    AnomalySeverity severity,
    String reason,
    Boolean blocking
) {}
