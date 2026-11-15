package tn.esprit.agentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.agentservice.dto.ExecuteRequest;
import tn.esprit.agentservice.dto.ExecuteResponse;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.event.AgentEvent;
import tn.esprit.agentservice.event.AgentEventPublisher;
import tn.esprit.agentservice.event.AgentEventType;
import tn.esprit.agentservice.exception.AgentExecutionException;
import tn.esprit.agentservice.exception.DownstreamServiceException;
import tn.esprit.agentservice.exception.DownstreamTimeoutException;
import tn.esprit.agentservice.exception.InvalidAgentConfigurationException;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(AgentExecutionService.class);

    private final AgentService agentService;
    private final AgentVersionService versionService;
    private final AgentVersionRepository versionRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AgentToolProvider> toolProvider;
    private final AgentEventPublisher eventPublisher;

    public AgentExecutionService(
            AgentService agentService,
            AgentVersionService versionService,
            AgentVersionRepository versionRepository,
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            ObjectProvider<AgentToolProvider> toolProvider,
            AgentEventPublisher eventPublisher
    ) {
        this.agentService = agentService;
        this.versionService = versionService;
        this.versionRepository = versionRepository;
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
        this.toolProvider = toolProvider;
        this.eventPublisher = eventPublisher;
    }

    public ExecuteResponse execute(Long agentId, ExecuteRequest request, Authentication authentication) {
        var agent = agentService.requireAgent(agentId);
        agentService.assertCanManage(agent, authentication);

        AgentVersion version = request.versionId() == null
                ? versionRepository.findFirstByAgentIdAndActiveTrue(agentId)
                        .orElseThrow(() -> new InvalidAgentConfigurationException(
                                "The agent has no active version."
                        ))
                : versionService.requireVersion(agentId, request.versionId());
        AgentConfiguration configuration = version.getConfiguration();
        if (configuration == null) {
            throw new InvalidAgentConfigurationException("The selected version has no configuration.");
        }

        long startedAt = System.nanoTime();
        CompletableFuture<ChatResponse> call = CompletableFuture.supplyAsync(
                () -> callLlm(version, configuration, request)
        );
        try {
            ChatResponse response = call.get(configuration.getTimeoutSeconds(), TimeUnit.SECONDS);
            String output = response == null || response.getResult() == null
                    || response.getResult().getOutput() == null
                    ? null
                    : response.getResult().getOutput().getText();
            if (output == null || output.isBlank()) {
                logger.warn("LLM returned no output for agent {} and version {}", agentId, version.getId());
                throw failed(HttpStatus.BAD_GATEWAY, "The LLM returned no output.", version.getId(), startedAt);
            }
            long tokensUsed = response.getMetadata() == null
                    || response.getMetadata().getUsage() == null
                    || response.getMetadata().getUsage().getTotalTokens() == null
                    ? 0
                    : response.getMetadata().getUsage().getTotalTokens();
            eventPublisher.publish(AgentEvent.create(
                    AgentEventType.AGENT_EXECUTED,
                    "agent-service",
                    agentId,
                    version.getId()
            ));
            return new ExecuteResponse(
                    "SUCCESS",
                    output,
                    tokensUsed,
                    elapsedMillis(startedAt),
                    version.getId()
            );
        } catch (TimeoutException exception) {
            call.cancel(true);
            logger.warn("Agent execution timed out for agent {} and version {}", agentId, version.getId());
            throw failed(HttpStatus.GATEWAY_TIMEOUT, "Agent execution timed out.", version.getId(), startedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            logger.warn("Agent execution interrupted for agent {} and version {}", agentId, version.getId(), exception);
            throw failed(HttpStatus.BAD_GATEWAY, "Agent execution was interrupted.", version.getId(), startedAt);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (hasTimeoutCause(cause)) {
                logger.warn("Agent tool or LLM call timed out for agent {} and version {}", agentId, version.getId(), cause);
                throw failed(HttpStatus.GATEWAY_TIMEOUT, "Agent execution timed out.", version.getId(), startedAt);
            }
            if (cause instanceof DownstreamTimeoutException) {
                logger.warn("Tool service timed out for agent {} and version {}", agentId, version.getId(), cause);
                throw failed(HttpStatus.GATEWAY_TIMEOUT, "Tool service timed out.", version.getId(), startedAt);
            }
            if (cause instanceof DownstreamServiceException) {
                logger.warn("Tool service failed for agent {} and version {}", agentId, version.getId(), cause);
                throw failed(HttpStatus.BAD_GATEWAY, "Tool service execution failed.", version.getId(), startedAt);
            }
            logger.warn("Agent execution failed for agent {} and version {}", agentId, version.getId(), cause);
            throw failed(HttpStatus.BAD_GATEWAY, "Agent execution failed.", version.getId(), startedAt);
        }
    }

    private ChatResponse callLlm(
            AgentVersion version,
            AgentConfiguration configuration,
            ExecuteRequest request
    ) {
        List<ToolCallback> callbacks = getAllowedCallbacks(configuration, request.context());
        AtomicInteger turns = new AtomicInteger(1);
        List<ToolCallback> limitedCallbacks = callbacks.stream()
                .map(callback -> limitTurns(callback, configuration.getMaxTurns(), turns))
                .toList();

        ChatClient.ChatClientRequestSpec prompt = chatClientBuilder.build()
                .prompt()
                .system(version.getSystemPrompt())
                .user(buildUserPrompt(request))
                .options(OpenAiChatOptions.builder()
                        .model(version.getModel())
                        .temperature(configuration.getTemperature())
                        .timeout(Duration.ofSeconds(configuration.getTimeoutSeconds())));
        if (!limitedCallbacks.isEmpty()) {
            prompt.toolCallbacks(limitedCallbacks);
        }

        // TODO: Replace the OpenAI-specific options when the production LLM provider is finalized.
        return prompt.call().chatResponse();
    }

    private List<ToolCallback> getAllowedCallbacks(
            AgentConfiguration configuration,
            Map<String, Object> context
    ) {
        List<Long> allowedToolIds = configuration.getAllowedToolIds();
        if (allowedToolIds == null || allowedToolIds.isEmpty()) {
            return List.of();
        }
        AgentToolProvider provider = toolProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("M2 tool integration is not configured.");
        }
        Set<Long> allowedIds = Set.copyOf(allowedToolIds);
        Map<Long, ToolCallback> callbacksByToolId = provider.getToolCallbacks(allowedIds, context);
        if (callbacksByToolId == null) {
            throw new IllegalStateException("M2 tool integration returned no tool mapping.");
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacksByToolId.forEach((toolId, callback) -> {
            if (allowedIds.contains(toolId)) {
                if (callback == null) {
                    throw new IllegalStateException("M2 returned an invalid tool callback.");
                }
                callbacks.add(callback);
            }
        });
        return callbacks;
    }

    private ToolCallback limitTurns(ToolCallback callback, int maxTurns, AtomicInteger turns) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return callback.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                if (turns.incrementAndGet() > maxTurns) {
                    throw new IllegalStateException("The configured maximum number of turns was exceeded.");
                }
                return callback.call(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                if (turns.incrementAndGet() > maxTurns) {
                    throw new IllegalStateException("The configured maximum number of turns was exceeded.");
                }
                return callback.call(toolInput, toolContext);
            }
        };
    }

    private String buildUserPrompt(ExecuteRequest request) {
        if (request.context() == null || request.context().isEmpty()) {
            return request.input();
        }
        try {
            return "Context:\n" + objectMapper.writeValueAsString(request.context())
                    + "\n\nInput:\n" + request.input();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("The execution context could not be serialized.", exception);
        }
    }

    private AgentExecutionException failed(HttpStatus status, String message, Long versionId, long startedAt) {
        return new AgentExecutionException(
                status,
                message,
                new ExecuteResponse("FAILED", message, 0, elapsedMillis(startedAt), versionId)
        );
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
        }
        return false;
    }
}
