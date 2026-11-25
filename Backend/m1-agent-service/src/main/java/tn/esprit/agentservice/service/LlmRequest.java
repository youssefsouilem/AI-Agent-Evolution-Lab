package tn.esprit.agentservice.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public record LlmRequest(
        String provider,
        String model,
        String systemPrompt,
        String userMessage,
        LlmSettings settings,
        List<ToolCallback> toolCallbacks
) {
    public LlmRequest(
            String model,
            String systemPrompt,
            String userMessage,
            LlmSettings settings,
            List<ToolCallback> toolCallbacks
    ) {
        this(null, model, systemPrompt, userMessage, settings, toolCallbacks);
    }
}
