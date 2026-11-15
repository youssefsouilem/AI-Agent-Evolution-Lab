package tn.esprit.agentservice.client;

import feign.RetryableException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tn.esprit.agentservice.exception.DownstreamTimeoutException;
import tn.esprit.agentservice.exception.DownstreamServiceException;
import tn.esprit.agentservice.service.AgentToolProvider;

import java.util.Map;
import java.util.Set;

@Component
public class FeignAgentToolProvider implements AgentToolProvider {

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":true}";

    private final ToolClient toolClient;

    public FeignAgentToolProvider(ToolClient toolClient) {
        this.toolClient = toolClient;
    }

    @Override
    public Map<Long, ToolCallback> getToolCallbacks(Set<Long> allowedToolIds, Map<String, Object> context) {
        return allowedToolIds.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                id -> id,
                id -> callbackFor(id, context)
        ));
    }

    private ToolCallback callbackFor(Long toolId, Map<String, Object> context) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("tool_" + toolId)
                        .description("Execute the permitted tool with id " + toolId + ".")
                        .inputSchema(INPUT_SCHEMA)
                        .build();
            }

            @Override
            public String call(String input) {
                try {
                    ToolExecuteResponse response = toolClient.execute(
                            toolId,
                            new ToolExecuteRequest(input, context)
                    );
                    if (response == null || response.output() == null) {
                        throw new DownstreamServiceException("Tool service returned an empty response.");
                    }
                    return response.output();
                } catch (RetryableException exception) {
                    throw OpenFeignConfig.mapTransportFailure(exception);
                } catch (DownstreamTimeoutException exception) {
                    throw exception;
                } catch (DownstreamServiceException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new DownstreamServiceException("Tool service execution failed.", exception);
                }
            }

            @Override
            public String call(String input, org.springframework.ai.chat.model.ToolContext toolContext) {
                return call(input);
            }
        };
    }
}
