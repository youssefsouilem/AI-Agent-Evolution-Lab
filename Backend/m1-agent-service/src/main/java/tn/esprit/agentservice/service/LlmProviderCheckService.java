package tn.esprit.agentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.config.CurrentUser;
import tn.esprit.agentservice.dto.LlmProviderCheckResponse;
import tn.esprit.agentservice.entity.AdminAudit;
import tn.esprit.agentservice.exception.ModelNotAvailableException;
import tn.esprit.agentservice.exception.ProviderNotConfiguredException;
import tn.esprit.agentservice.exception.RateLimitedException;
import tn.esprit.agentservice.repository.AdminAuditRepository;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LlmProviderCheckService {
    private final ModelCatalogService catalog;
    private final ProviderRouter router;
    private final LlmFailureClassifier failureClassifier;
    private final AdminAuditRepository audits;
    private final ObjectMapper objectMapper;
    private final int maxPerHour;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> checks = new ConcurrentHashMap<>();

    public LlmProviderCheckService(
            ModelCatalogService catalog,
            ProviderRouter router,
            LlmFailureClassifier failureClassifier,
            AdminAuditRepository audits,
            ObjectMapper objectMapper,
            @Value("${m1.llm.check-max-per-hour:20}") int maxPerHour
    ) {
        this.catalog = catalog;
        this.router = router;
        this.failureClassifier = failureClassifier;
        this.audits = audits;
        this.objectMapper = objectMapper;
        this.maxPerHour = maxPerHour;
    }

    @Transactional
    public LlmProviderCheckResponse check(String providerId, CurrentUser actor) {
        countCheck(actor.id());
        ModelCatalogService.RecommendedModel recommended =
                catalog.recommendedModel(providerId);
        long started = System.nanoTime();
        boolean ok = false;
        String errorCode = null;
        try {
            if (!catalog.isAvailable(providerId)) {
                try {
                    catalog.apiKey(providerId);
                    errorCode = "MODEL_NOT_AVAILABLE";
                } catch (ProviderNotConfiguredException exception) {
                    errorCode = "PROVIDER_NOT_CONFIGURED";
                }
                if (errorCode == null) {
                    errorCode = "MODEL_NOT_AVAILABLE";
                }
            } else {
                LlmResult result = router.call(new LlmRequest(providerId, recommended.model(),
                        "Réponds par OK.", "Réponds par OK.", new LlmSettings(0, 16, 5), List.of()));
                ok = result != null && result.text() != null && !result.text().isBlank();
                if (!ok) {
                    errorCode = "EMPTY_RESPONSE";
                }
            }
        } catch (RuntimeException exception) {
            if (exception instanceof ProviderNotConfiguredException) {
                errorCode = "PROVIDER_NOT_CONFIGURED";
            } else if (exception instanceof ModelNotAvailableException) {
                errorCode = "MODEL_NOT_AVAILABLE";
            } else {
                errorCode = failureClassifier.classify(exception).code();
            }
        }
        long latencyMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        writeAudit(actor, providerId, recommended.model(), ok, latencyMs, errorCode);
        return new LlmProviderCheckResponse(providerId, recommended.model(), ok, latencyMs, errorCode);
    }

    private void countCheck(String actorId) {
        ArrayDeque<Instant> timestamps = checks.computeIfAbsent(actorId, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant threshold = Instant.now().minusSeconds(3600);
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(threshold)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxPerHour) {
                throw new RateLimitedException();
            }
            timestamps.addLast(Instant.now());
        }
    }

    private void writeAudit(
            CurrentUser actor,
            String provider,
            String model,
            boolean ok,
            long latencyMs,
            String errorCode
    ) {
        AdminAudit audit = new AdminAudit();
        audit.setActorId(actor.id());
        audit.setAction("LLM_PROVIDER_CHECKED");
        try {
            audit.setDetails(objectMapper.writeValueAsString(Map.of(
                    "provider", provider,
                    "model", model,
                    "ok", ok,
                    "latencyMs", latencyMs,
                    "errorCode", errorCode == null ? "" : errorCode
            )));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de sérialiser l'audit du fournisseur.", exception);
        }
        audits.save(audit);
    }
}
