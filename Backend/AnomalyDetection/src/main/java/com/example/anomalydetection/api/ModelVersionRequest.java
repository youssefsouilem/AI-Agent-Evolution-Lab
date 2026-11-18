package com.example.anomalydetection.api;

public record ModelVersionRequest(String name, String algorithm, Double contamination,
                                  Double thresholdScore, String features, Boolean active) {
}
