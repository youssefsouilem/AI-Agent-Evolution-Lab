package tn.esprit.agentservice.service;

public record LlmResult(
        String text,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
