package tn.esprit.agentservice.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.Map;
import java.util.Set;

public interface AgentToolProvider {

    Map<Long, ToolCallback> getToolCallbacks(Set<Long> allowedToolIds, Map<String, Object> context);
}
