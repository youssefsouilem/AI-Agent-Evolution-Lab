package tn.esprit.agentservice.client;

import java.util.Map;

public record ToolExecuteRequest(String input, Map<String, Object> context) {
}
