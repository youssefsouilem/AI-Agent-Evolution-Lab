package tn.esprit.agentservice.dto;

public record LlmProviderCheckResponse(
        String provider,
        String model,
        boolean ok,
        long latencyMs,
        String errorCode
) { }
