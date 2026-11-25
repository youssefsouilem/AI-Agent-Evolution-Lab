package tn.esprit.agentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.client.ToolAuthorizationContext;
import tn.esprit.agentservice.dto.ExecuteRequest;
import tn.esprit.agentservice.dto.ExecuteResponse;
import tn.esprit.agentservice.entity.Agent;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.entity.AgentVersion;
import tn.esprit.agentservice.entity.ExecutionStatus;
import tn.esprit.agentservice.exception.DownstreamServiceException;
import tn.esprit.agentservice.exception.DownstreamTimeoutException;
import tn.esprit.agentservice.exception.ExecutionFailedException;
import tn.esprit.agentservice.exception.ExecutionLimitException;
import tn.esprit.agentservice.exception.InvalidConfigurationException;
import tn.esprit.agentservice.exception.ResourceNotFoundException;
import tn.esprit.agentservice.repository.AgentVersionRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AgentExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(AgentExecutionService.class);
    private final AgentService agentService;
    private final AgentVersionService versionService;
    private final AgentVersionRepository versionRepository;
    private final ObjectProvider<AgentToolProvider> toolProvider;
    private final TunableParameterCatalog catalog;
    private final LlmGateway llmGateway;
    private final LlmFailureClassifier failureClassifier;
    private final AgentExecutionStore store;
    private final ObjectMapper objectMapper;
    private final ExecutorService executionExecutor;
    private final AgentAccessPolicy accessPolicy;
    private final ModelCatalogService modelCatalog;
    private final ProviderRouter providerRouter;

    public AgentExecutionService(
            AgentService agentService,
            AgentVersionService versionService,
            AgentVersionRepository versionRepository,
            ObjectProvider<AgentToolProvider> toolProvider,
            TunableParameterCatalog catalog,
            LlmGateway llmGateway,
            LlmFailureClassifier failureClassifier,
            AgentExecutionStore store,
            ObjectMapper objectMapper,
            ExecutorService executionExecutor,
            String ignoredStartupKey
    ) {
        this(agentService, versionService, versionRepository, toolProvider, catalog, llmGateway,
                failureClassifier, store, objectMapper, executionExecutor, ignoredStartupKey,
                new AgentAccessPolicy(), null, null);
    }

    @Autowired
    public AgentExecutionService(
            AgentService agentService,
            AgentVersionService versionService,
            AgentVersionRepository versionRepository,
            ObjectProvider<AgentToolProvider> toolProvider,
            TunableParameterCatalog catalog,
            LlmGateway llmGateway,
            LlmFailureClassifier failureClassifier,
            AgentExecutionStore store,
            ObjectMapper objectMapper,
            ExecutorService executionExecutor,
            @Value("unused") String ignoredStartupKey,
            AgentAccessPolicy accessPolicy,
            ModelCatalogService modelCatalog,
            ProviderRouter providerRouter
    ) {
        this.agentService = agentService;
        this.versionService = versionService;
        this.versionRepository = versionRepository;
        this.toolProvider = toolProvider;
        this.catalog = catalog;
        this.llmGateway = llmGateway;
        this.failureClassifier = failureClassifier;
        this.store = store;
        this.objectMapper = objectMapper;
        this.executionExecutor = executionExecutor;
        this.accessPolicy = accessPolicy;
        this.modelCatalog = modelCatalog;
        this.providerRouter = providerRouter;
    }

    public ExecuteResponse execute(Long agentId, ExecuteRequest request, CurrentUser user) {
        Agent agent = agentService.loadOwnedAgentForUpdate(agentId, user);
        accessPolicy.requireWrite(agent, user);
        if (agent.getStatus() == tn.esprit.agentservice.entity.AgentStatus.ARCHIVED) {
            throw new InvalidConfigurationException("Un agent archivé ne peut pas être exécuté.");
        }
        AgentVersion version = request.versionId() == null
                ? versionRepository.findFirstByAgentIdAndActiveTrue(agentId)
                        .orElseThrow(() -> new InvalidConfigurationException(
                                "Aucune version active n'est disponible pour cet agent."
                        ))
                : versionService.requireVersion(agentId, request.versionId());
        String providerId = modelCatalog == null ? version.getProvider()
                : modelCatalog.resolveProvider(version.getProvider(), version.getModel());
        AgentConfiguration storedConfiguration = version.getConfiguration();
        if (storedConfiguration == null) {
            throw new InvalidConfigurationException("La version sélectionnée ne possède pas de configuration.");
        }

        AgentConfiguration configuration = copyConfiguration(storedConfiguration);
        if (configuration.getMaxTurns() <= 0) {
            configuration.setMaxTurns(3);
        }
        if (configuration.getTimeoutSeconds() <= 0) {
            configuration.setTimeoutSeconds(30);
        }
        validateEffectiveConfiguration(configuration);
        catalog.applyParams(configuration, request.overrides());
        Map<String, Object> appliedSettings = appliedSettings(version, configuration);
        String contextJson = request.context() == null ? null : request.context().toString();
        String userMessage = userMessage(request);

        var execution = store.start(agentId, version.getId(), version.getVersionNumber(), providerId,
                user.id(), user.role(),
                request.input(), contextJson, request.overrides(), appliedSettings, version.getModel());
        long startNanos = System.nanoTime();
        ToolCallRecorder recorder = new ToolCallRecorder();
        String callerAuthorization = RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getHeader("Authorization") : null;
        LlmResult result = null;
        String finalOutput = null;
        String errorCode = null;
        String errorMessage = null;
        ExecutionFailedException failure = null;
        RuntimeException finalizationFailure = null;
        ExecutionStatus finalStatus = ExecutionStatus.FAILED;
        Future<LlmResult> call = null;
        try {
            List<org.springframework.ai.tool.ToolCallback> callbacks = allowedCallbacks(
                    configuration, version.getId(), recorder
            );
            AtomicInteger calls = new AtomicInteger();
            List<org.springframework.ai.tool.ToolCallback> limitedCallbacks = callbacks.stream()
                    .map(callback -> limitTurns(callback, configuration.getMaxTurns(), calls, recorder))
                    .toList();
            LlmRequest llmRequest = new LlmRequest(
                    providerId,
                    version.getModel(),
                    styledSystemPrompt(version.getSystemPrompt(), configuration.getStyle()),
                    userMessage,
                    new LlmSettings(configuration.getTemperature(),
                            configuration.getMaxTokens() == null ? 1024 : configuration.getMaxTokens(),
                            effectiveTimeout(configuration)),
                    limitedCallbacks
            );
            call = executionExecutor.submit(() -> {
                ToolAuthorizationContext.set(callerAuthorization);
                try {
                    return providerRouter == null ? llmGateway.call(llmRequest) : providerRouter.call(llmRequest);
                } finally {
                    ToolAuthorizationContext.clear();
                }
            });
            result = call.get(effectiveTimeout(configuration), TimeUnit.SECONDS);
            if (calls.get() > configuration.getMaxTurns()) {
                throw new ExecutionLimitException("Le nombre maximal d'appels d'outils est dépassé.");
            }
            if (result == null || result.text() == null || result.text().isBlank()) {
                throw failed("EMPTY_RESPONSE", HttpStatus.BAD_GATEWAY,
                        "Le fournisseur du modèle n'a retourné aucune réponse.", execution.getId(), version.getId(),
                        elapsed(startNanos), tokensPrompt(result), tokensCompletion(result), tokensTotal(result),
                        recorder.count(), null);
            }
            String safeOutput = ErrorMessageSanitizer.redact(result.text());
            finalOutput = safeOutput.substring(0, Math.min(100000, safeOutput.length()));
            finalStatus = ExecutionStatus.COMPLETED;
        } catch (ExecutionFailedException exception) {
            failure = exception;
            errorCode = exception.getErrorCode();
            errorMessage = exception.getMessage();
        } catch (TimeoutException exception) {
            if (call != null) {
                call.cancel(true);
            }
            failure = failed("TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                    "Le délai maximal d'exécution est dépassé.", execution.getId(), version.getId(),
                    elapsed(startNanos), tokensPrompt(result), tokensCompletion(result), tokensTotal(result),
                    recorder.count(), exception);
            errorCode = failure.getErrorCode();
            errorMessage = failure.getMessage();
        } catch (InterruptedException exception) {
            if (call != null) {
                call.cancel(true);
            }
            Thread.currentThread().interrupt();
            failure = failed("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                    "L'exécution a été interrompue.", execution.getId(), version.getId(),
                    elapsed(startNanos), tokensPrompt(result), tokensCompletion(result), tokensTotal(result),
                    recorder.count(), exception);
            errorCode = failure.getErrorCode();
            errorMessage = failure.getMessage();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            failure = classifyFailure(cause, execution.getId(), version.getId(), elapsed(startNanos),
                    result, recorder.count());
            errorCode = failure.getErrorCode();
            errorMessage = failure.getMessage();
        } catch (ExecutionLimitException exception) {
            failure = failed("TOOL_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Le nombre maximal d'appels d'outils est dépassé.", execution.getId(), version.getId(),
                    elapsed(startNanos), tokensPrompt(result), tokensCompletion(result), tokensTotal(result),
                    recorder.count(), exception);
            errorCode = failure.getErrorCode();
            errorMessage = failure.getMessage();
        } catch (RuntimeException exception) {
            failure = classifyFailure(exception, execution.getId(), version.getId(), elapsed(startNanos),
                    result, recorder.count());
            errorCode = failure.getErrorCode();
            errorMessage = failure.getMessage();
        } finally {
            long duration = elapsed(startNanos);
            if (failure != null) {
                duration = failure.getDurationMs() == null ? duration : failure.getDurationMs();
            }
            try {
                store.finish(execution.getId(), finalStatus, errorCode, errorMessage, finalOutput, duration,
                        tokensPrompt(result), tokensCompletion(result), tokensTotal(result), recorder.count(),
                        writeToolCalls(recorder.snapshot()));
            } catch (RuntimeException exception) {
                finalizationFailure = exception;
                finalStatus = ExecutionStatus.FAILED;
                errorCode = "INTERNAL_ERROR";
                errorMessage = "Impossible d'enregistrer le résultat de l'exécution.";
                logger.error("Could not finalize execution {}.", execution.getId(), exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
        if (finalizationFailure != null) {
            throw failed("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible d'enregistrer le résultat de l'exécution.", execution.getId(), version.getId(),
                    elapsed(startNanos), tokensPrompt(result), tokensCompletion(result), tokensTotal(result),
                    recorder.count(), finalizationFailure);
        }
        return new ExecuteResponse(execution.getId(), "COMPLETED", finalOutput, tokensTotal(result),
                tokensPrompt(result), tokensCompletion(result), elapsed(startNanos), version.getId(), recorder.count(),
                request.overrides(), appliedSettings);
    }

    private List<org.springframework.ai.tool.ToolCallback> allowedCallbacks(
            AgentConfiguration configuration, Long versionId, ToolCallRecorder recorder
    ) {
        List<Long> allowedIds = configuration.getAllowedToolIds();
        if (allowedIds == null || allowedIds.isEmpty()) {
            return List.of();
        }
        AgentToolProvider provider = toolProvider.getIfAvailable();
        if (provider == null) {
            throw new DownstreamServiceException("L'intégration des outils est indisponible.");
        }
        return new ArrayList<>(provider.getToolCallbacks(allowedIds, versionId, recorder).values());
    }

    private org.springframework.ai.tool.ToolCallback limitTurns(
            org.springframework.ai.tool.ToolCallback callback,
            int maxTurns,
            AtomicInteger calls,
            ToolCallRecorder recorder
    ) {
        return new org.springframework.ai.tool.ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return callback.getToolDefinition();
            }

            @Override
            public String call(String input) {
                int callNumber = calls.incrementAndGet();
                if (callNumber > maxTurns) {
                    recorder.add(new ToolCallRecord(callback.getToolDefinition().name(), "DENIED", 0));
                    throw new ExecutionLimitException("Le nombre maximal d'appels d'outils est dépassé.");
                }
                return callback.call(input);
            }

            @Override
            public String call(String input, org.springframework.ai.chat.model.ToolContext context) {
                return call(input);
            }
        };
    }

    private ExecutionFailedException classifyFailure(
            Throwable cause, Long executionId, Long versionId, long duration, LlmResult result, int toolCalls
    ) {
        if (findCause(cause, ExecutionLimitException.class) != null) {
            return failed("TOOL_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Le nombre maximal d'appels d'outils est dépassé.", executionId, versionId,
                    duration, tokensPrompt(result), tokensCompletion(result), tokensTotal(result), toolCalls, cause);
        }
        if (findCause(cause, DownstreamTimeoutException.class) != null) {
            return failed("TOOL_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                    "Le service d'outils a dépassé le délai maximal.", executionId, versionId,
                    duration, tokensPrompt(result), tokensCompletion(result), tokensTotal(result), toolCalls, cause);
        }
        if (findCause(cause, DownstreamServiceException.class) != null) {
            return failed("TOOL_SERVICE_UNAVAILABLE", HttpStatus.BAD_GATEWAY,
                    "Le service d'outils est indisponible.", executionId, versionId,
                    duration, tokensPrompt(result), tokensCompletion(result), tokensTotal(result), toolCalls, cause);
        }
        LlmFailureClassifier.Failure classification = failureClassifier.classify(cause);
        return failed(classification.code(), classification.status(), classification.message(),
                executionId, versionId, duration, tokensPrompt(result), tokensCompletion(result),
                tokensTotal(result), toolCalls, cause);
    }

    private <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    private ExecutionFailedException failed(
            String code, HttpStatus status, String message, Long executionId, Long versionId, Long duration,
            Integer promptTokens, Integer completionTokens, Integer totalTokens, int toolCalls, Throwable cause
    ) {
        return new ExecutionFailedException(code, status, message, executionId, versionId, duration,
                promptTokens, completionTokens, totalTokens, toolCalls, cause);
    }

    private AgentConfiguration copyConfiguration(AgentConfiguration source) {
        return AgentConfiguration.builder()
                .temperature(source.getTemperature())
                .maxTokens(source.getMaxTokens())
                .style(source.getStyle())
                .maxTurns(source.getMaxTurns())
                .timeoutSeconds(source.getTimeoutSeconds())
                .allowedToolIds(source.getAllowedToolIds() == null
                        ? new ArrayList<>() : new ArrayList<>(source.getAllowedToolIds()))
                .build();
    }

    private void validateEffectiveConfiguration(AgentConfiguration configuration) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("temperature", configuration.getTemperature());
        values.put("maxTurns", configuration.getMaxTurns());
        values.put("timeoutSeconds", configuration.getTimeoutSeconds());
        if (configuration.getMaxTokens() != null) {
            values.put("maxTokens", configuration.getMaxTokens());
        }
        if (configuration.getStyle() != null) {
            values.put("style", configuration.getStyle());
        }
        catalog.applyParams(configuration, values);
    }

    private Map<String, Object> appliedSettings(AgentVersion version, AgentConfiguration configuration) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("model", version.getModel());
        settings.put("temperature", configuration.getTemperature());
        settings.put("maxTokens", configuration.getMaxTokens() == null ? 1024 : configuration.getMaxTokens());
        settings.put("maxTurns", configuration.getMaxTurns() <= 0 ? 3 : configuration.getMaxTurns());
        settings.put("timeoutSeconds", effectiveTimeout(configuration));
        settings.put("style", configuration.getStyle() == null ? "concise" : configuration.getStyle());
        settings.put("allowedToolIds", configuration.getAllowedToolIds() == null
                ? List.of() : List.copyOf(configuration.getAllowedToolIds()));
        return settings;
    }

    private int effectiveTimeout(AgentConfiguration configuration) {
        return configuration.getTimeoutSeconds() <= 0 ? 30 : configuration.getTimeoutSeconds();
    }

    private String styledSystemPrompt(String prompt, String style) {
        String instruction = "detaille".equals(style)
                ? "Réponds de manière détaillée et structurée."
                : "Réponds de manière concise.";
        return prompt == null || prompt.isBlank() ? instruction : prompt + "\n" + instruction;
    }

    private String userMessage(ExecuteRequest request) {
        if (request.context() == null || request.context().isNull()) {
            return request.input();
        }
        String context = request.context().isTextual() ? request.context().asText() : request.context().toString();
        return "Context:\n" + context + "\n\nTask:\n" + request.input();
    }

    private String writeToolCalls(List<ToolCallRecord> calls) {
        try {
            return objectMapper.writeValueAsString(calls);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de sérialiser l'historique des appels d'outils.", exception);
        }
    }

    private Integer tokensPrompt(LlmResult result) { return result == null ? null : result.promptTokens(); }
    private Integer tokensCompletion(LlmResult result) { return result == null ? null : result.completionTokens(); }
    private Integer tokensTotal(LlmResult result) { return result == null ? null : result.totalTokens(); }
    private long elapsed(long started) { return Duration.ofNanos(System.nanoTime() - started).toMillis(); }
}
