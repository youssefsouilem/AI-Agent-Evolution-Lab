package tn.esprit.agentservice.service;

public record LlmSettings(double temperature, int maxTokens, int timeoutSeconds) {
}
