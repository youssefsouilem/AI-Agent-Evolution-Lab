package tn.esprit.mission.dto;

import tn.esprit.mission.enums.AnomalySeverity;

// Source metadata keeps a fixture from being mistaken for a real M6 diagnosis.
public record MonitoringCheckResponse(
    Long executionId,
    String source,
    boolean simulated,
    Boolean anomalyDetected,
    AnomalySeverity severity,
    String reason,
    Boolean blocking
) {}
