package tn.esprit.agentservice.dto;

public record AgentProposal(
        String name,
        String description,
        String systemPrompt,
        String model,
        double temperature,
        int maxTokens,
        int maxTurns,
        int timeoutSeconds,
        String style
) { }
