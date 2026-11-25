package tn.esprit.mission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tn.esprit.mission.entity.*;
import tn.esprit.mission.repository.MissionEventOutboxRepository;

@Configuration
@EnableScheduling
class MissionEventScheduling {}

@Service
public class MissionEventPublisher {
    private final MissionEventOutboxRepository outbox;
    private final ObjectMapper mapper;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final String exchange;
    private final String routingKey;
    public MissionEventPublisher(MissionEventOutboxRepository outbox, ObjectMapper mapper, RabbitTemplate rabbit,
        PlatformTransactionManager manager, @Value("${events.enabled:false}") boolean enabled,
        @Value("${events.exchange:mission.events}") String exchange, @Value("${events.routing-key:mission.lifecycle}") String routingKey) {
        this.outbox = outbox; this.mapper = mapper; this.rabbit = rabbit; this.transactions = new TransactionTemplate(manager);
        this.enabled = enabled; this.exchange = exchange; this.routingKey = routingKey;
    }
    // Called inside the same transaction that commits the execution's terminal state.
    public void enqueue(MissionExecution execution) {
        String type = "MISSION_" + execution.getStatus().name();
        if (outbox.existsByExecutionIdAndEventType(execution.getId(), type)) return;
        var event = new MissionEventOutbox();
        event.id = UUID.randomUUID().toString(); event.executionId = execution.getId(); event.eventType = type;
        event.createdAt = Instant.now(); event.nextAttemptAt = event.createdAt;
        var body = mapper.createObjectNode();
        body.put("eventId", event.id).put("schemaVersion", 1).put("eventType", type)
            .put("occurredAt", event.createdAt.toString()).put("missionId", execution.getMissionId())
            .put("executionId", execution.getId()).put("status", execution.getStatus().name())
            .put("metricsSource", execution.getMetricsSource()).put("attempts", execution.getAttemptsUsed());
        if (execution.getTotalTokensUsed() == null) body.putNull("tokensUsed"); else body.put("tokensUsed", execution.getTotalTokensUsed());
        event.payload = body.toString(); outbox.save(event);
    }
    @Scheduled(fixedDelayString = "${events.poll-ms:5000}")
    public void publishPending() {
        if (!enabled) return;
        for (var event : outbox.findTop20ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now())) {
            try {
                var properties = new MessageProperties(); properties.setContentType("application/json");
                properties.setMessageId(event.id); properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                var correlation = new CorrelationData(event.id);
                rabbit.send(exchange, routingKey, new Message(event.payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), properties), correlation);
                var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirmation.isAck() || correlation.getReturned() != null) throw new IllegalStateException("Livraison non confirmée");
                transactions.executeWithoutResult(tx -> {
                    var row = outbox.findById(event.id).orElseThrow(); row.publishedAt = Instant.now(); row.lastError = null;
                });
            } catch (Exception error) {
                transactions.executeWithoutResult(tx -> {
                    var row = outbox.findById(event.id).orElseThrow(); row.attempts++;
                    row.nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 5L << Math.min(row.attempts, 5)));
                    row.lastError = "Broker indisponible, retour non routé ou confirmation absente";
                });
            }
        }
    }
}
