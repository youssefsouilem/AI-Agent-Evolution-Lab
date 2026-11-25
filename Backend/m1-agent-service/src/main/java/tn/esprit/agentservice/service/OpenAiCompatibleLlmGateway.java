package tn.esprit.agentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.esprit.agentservice.exception.ProviderNotConfiguredException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OpenAiCompatibleLlmGateway implements LlmGateway {
    private final ModelCatalogService catalog;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmGateway(ModelCatalogService catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResult call(LlmRequest request) {
        if (request.provider() == null || request.provider().isBlank()) {
            throw new IllegalArgumentException("Un fournisseur doit être résolu avant l'appel.");
        }
        return call(request.provider(), request);
    }

    public LlmResult call(String providerId, LlmRequest request) {
        String apiKey = catalog.apiKey(providerId);
        String baseUrl = catalog.baseUrl(providerId);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ProviderNotConfiguredException();
        }

        Duration timeout = Duration.ofSeconds(Math.max(1, request.settings().timeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                .requestFactory(requestFactory)
                .build();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        messages.add(Map.of("role", "user", "content", request.userMessage()));
        Map<String, org.springframework.ai.tool.ToolCallback> callbacks = request.toolCallbacks().stream()
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(),
                        Function.identity(), (first, ignored) -> first, LinkedHashMap::new));

        for (int attempt = 0; attempt <= 5; attempt++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", request.model());
            payload.put("messages", messages);
            payload.put("temperature", request.settings().temperature());
            payload.put("max_tokens", request.settings().maxTokens());
            if (!callbacks.isEmpty()) {
                payload.put("tools", callbacks.values().stream().map(callback -> {
                    var definition = callback.getToolDefinition();
                    Object schema;
                    try {
                        schema = objectMapper.readValue(definition.inputSchema(), Object.class);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalStateException("Le schéma d'un outil est invalide.");
                    }
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", definition.name());
                    function.put("description", definition.description());
                    function.put("parameters", schema);
                    return Map.of("type", "function", "function", function);
                }).toList());
            }

            String responseBody = client.post()
                    .uri("chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (requestMessage, clientResponse) -> {
                        throw new ProviderHttpException(clientResponse.getStatusCode().value());
                    })
                    .body(String.class);
            JsonNode response;
            try {
                response = responseBody == null ? null : objectMapper.readTree(responseBody);
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("Le fournisseur a retourné une réponse invalide.");
            }
            JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
            JsonNode toolCalls = message == null ? null : message.path("tool_calls");
            if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty() && !callbacks.isEmpty()) {
                messages.add(objectMapper.convertValue(message, LinkedHashMap.class));
                for (JsonNode toolCall : toolCalls) {
                    String name = toolCall.path("function").path("name").asText();
                    var callback = callbacks.get(name);
                    if (callback == null) {
                        throw new IllegalStateException("Le fournisseur a demandé un outil non autorisé.");
                    }
                    String arguments = toolCall.path("function").path("arguments").asText("{}");
                    String result = callback.call(arguments);
                    messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", toolCall.path("id").asText(),
                            "content", result == null ? "" : result
                    ));
                }
                continue;
            }
            String text = message == null || message.path("content").isNull()
                    ? null : message.path("content").asText(null);
            JsonNode usage = response == null ? null : response.path("usage");
            return new LlmResult(text, nullableInteger(usage, "prompt_tokens"),
                    nullableInteger(usage, "completion_tokens"), nullableInteger(usage, "total_tokens"));
        }
        return new LlmResult(null, null, null, null);
    }

    private Integer nullableInteger(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.canConvertToInt() ? null : value.intValue();
    }

    public static final class ProviderHttpException extends RuntimeException {
        private final int statusCode;

        public ProviderHttpException(int statusCode) {
            super("Le fournisseur du modèle a retourné une erreur HTTP.");
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
