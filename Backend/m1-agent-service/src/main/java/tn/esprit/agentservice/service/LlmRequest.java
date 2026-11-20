package tn.esprit.agentservice.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public record LlmRequest(
        String model,
        String systemPrompt,
        String userMessage,
        LlmSettings settings,
        List<ToolCallback> toolCallbacks
) {
}
