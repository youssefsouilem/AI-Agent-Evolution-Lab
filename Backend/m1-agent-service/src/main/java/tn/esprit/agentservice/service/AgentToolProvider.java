package tn.esprit.agentservice.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

public interface AgentToolProvider {

    Map<Long, ToolCallback> getToolCallbacks(
            java.util.List<Long> allowedToolIds,
            Long agentVersionId,
            ToolCallRecorder recorder
    );
}
