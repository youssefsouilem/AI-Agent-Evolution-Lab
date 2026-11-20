package tn.esprit.agentservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tn.esprit.agentservice.exception.DownstreamServiceException;
import tn.esprit.agentservice.exception.DownstreamTimeoutException;
import tn.esprit.agentservice.service.AgentToolProvider;
import tn.esprit.agentservice.service.ToolCallRecord;
import tn.esprit.agentservice.service.ToolCallRecorder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
public class FeignAgentToolProvider implements AgentToolProvider {
    private static final Logger logger = LoggerFactory.getLogger(FeignAgentToolProvider.class);
    private final ToolClient toolClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService toolExecutor;

    public FeignAgentToolProvider(
            ToolClient toolClient,
            ObjectMapper objectMapper,
            @Qualifier("agentExecutionExecutor") ExecutorService toolExecutor
    ) {
        this.toolClient = toolClient;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Map<Long, ToolCallback> getToolCallbacks(
            List<Long> allowedToolIds,
            Long agentVersionId,
            ToolCallRecorder recorder
    ) {
        if (allowedToolIds == null || allowedToolIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> allowed = Set.copyOf(allowedToolIds);
        final List<ToolDefinitionResponse> tools;
        try {
            tools = bounded(toolClient::list);
        } catch (RuntimeException exception) {
            throw mapFailure(exception, "Tool service is unavailable.");
        }
        if (tools == null) {
            throw new DownstreamServiceException("Tool service returned an invalid tool list.");
        }
        Map<Long, ToolCallback> callbacks = new LinkedHashMap<>();
        for (ToolDefinitionResponse tool : tools) {
            if (tool != null && tool.id() != null && allowed.contains(tool.id())) {
                callbacks.putIfAbsent(tool.id(), callback(tool, agentVersionId, recorder));
            }
        }
        return Map.copyOf(callbacks);
    }

    private ToolCallback callback(ToolDefinitionResponse tool, Long versionId, ToolCallRecorder recorder) {
        final String schema;
        try {
            schema = tool.inputSchema() == null ? "{\"type\":\"object\"}"
                    : tool.inputSchema() instanceof String value
                    ? value : objectMapper.writeValueAsString(tool.inputSchema());
        } catch (JsonProcessingException exception) {
            throw new DownstreamServiceException("Tool service returned an invalid input schema.", exception);
        }
        String name = safeName(tool.name(), tool.id());
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name)
                        .description(tool.description() == null ? "" : tool.description())
                        .inputSchema(schema).build();
            }

            @Override
            public String call(String input) {
                long started = System.nanoTime();
                String status = "ERROR";
                try {
                    Object arguments = objectMapper.readValue(input, Object.class);
                    ToolExecuteResponse response = bounded(() -> toolClient.execute(
                            tool.id(), new ToolExecuteRequest(versionId, arguments)
                    ));
                    if (response == null) {
                        throw new DownstreamServiceException("Tool service returned an invalid response.");
                    }
                    if ("DENIED".equalsIgnoreCase(response.status())) {
                        status = "DENIED";
                        return "{\"error\":\"Tool not authorized\"}";
                    }
                    if ("INVALID".equalsIgnoreCase(response.status())) {
                        status = "INVALID";
                        return "{\"error\":\"Invalid tool arguments\"}";
                    }
                    if (!"OK".equalsIgnoreCase(response.status())
                            && !"SUCCESS".equalsIgnoreCase(response.status())
                            && !"COMPLETED".equalsIgnoreCase(response.status())) {
                        throw new DownstreamServiceException("Tool service reported a failed execution.");
                    }
                    status = "OK";
                    return response.output() == null ? "" : response.output();
                } catch (JsonProcessingException exception) {
                    status = "INVALID";
                    return "{\"error\":\"Invalid tool arguments\"}";
                } catch (ToolHttpException exception) {
                    if (exception.getStatus() == 403) {
                        status = "DENIED";
                        logger.warn("SECURITY: M2 denied tool execution.");
                        return "{\"error\":\"Tool not authorized\"}";
                    }
                    if (exception.getStatus() == 422) {
                        status = "INVALID";
                        return "{\"error\":\"Invalid tool arguments\"}";
                    }
                    throw exception;
                } catch (RuntimeException exception) {
                    throw mapFailure(exception, "Tool service execution failed.");
                } finally {
                    recorder.add(new ToolCallRecord(name, status, elapsed(started)));
                }
            }

            @Override
            public String call(String input, org.springframework.ai.chat.model.ToolContext toolContext) {
                return call(input);
            }
        };
    }

    private RuntimeException mapFailure(RuntimeException exception, String message) {
        if (exception instanceof DownstreamTimeoutException || exception instanceof DownstreamServiceException) {
            return exception;
        }
        if (exception instanceof feign.RetryableException retryable) {
            return OpenFeignConfig.mapTransportFailure(retryable);
        }
        return new DownstreamServiceException(message, exception);
    }

    private <T> T bounded(Supplier<T> request) {
        Future<T> future = toolExecutor.submit(request::get);
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new DownstreamTimeoutException("Tool service request timed out.", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new DownstreamTimeoutException("Tool service request was interrupted.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new DownstreamServiceException("Tool service request failed.", cause);
        }
    }

    private String safeName(String name, Long id) {
        String source = name == null || name.isBlank() ? "tool_" + id : name;
        String sanitized = source.replaceAll("[^A-Za-z0-9_-]", "_");
        return sanitized.length() > 64 ? sanitized.substring(0, 64) : sanitized;
    }

    private long elapsed(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
