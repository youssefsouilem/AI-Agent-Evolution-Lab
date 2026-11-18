package com.example.anomalydetection.domain;

public enum AnomalyReason {
    NONE,
    EXECUTION_FAILURE,
    HIGH_LATENCY,
    HIGH_TOKEN_USAGE,
    EXCESSIVE_RETRIES,
    STATISTICAL_OUTLIER
}
