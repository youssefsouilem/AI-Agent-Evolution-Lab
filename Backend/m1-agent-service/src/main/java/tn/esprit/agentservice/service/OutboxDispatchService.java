package tn.esprit.agentservice.service;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.agentservice.entity.OutboxEvent;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Service
public class OutboxDispatchService {
    private static final Logger logger = LoggerFactory.getLogger(OutboxDispatchService.class);
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final int maxAttempts;
    private final long confirmTimeoutMs;

    public OutboxDispatchService(
            OutboxEventRepository repository,
            RabbitTemplate rabbitTemplate,
            @Value("${app.events.agent-exchange:agent.events}") String exchange,
            @Value("${m1.outbox.max-attempts:10}") int maxAttempts,
            @Value("${m1.outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.maxAttempts = maxAttempts;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Transactional
    public boolean dispatchOne() {
        var ready = repository.lockReadyEvents(OutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, 1));
        if (ready.isEmpty()) return false;
        OutboxEvent event = ready.get(0);
        try {
            CorrelationData correlation = new CorrelationData(event.getId().toString());
            rabbitTemplate.convertAndSend(exchange, event.getRoutingKey(), event.getPayload(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                failAttempt(event, "Le broker a refusé la confirmation.");
            } else if (correlation.getReturned() != null) {
                failAttempt(event, "Le message est non routable.");
            } else {
                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setLastError(null);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failAttempt(event, "Délai de confirmation du broker dépassé.");
            logger.warn("Outbox event {} delivery was interrupted; its retry state is {}.",
                    event.getId(), event.getStatus());
            return false;
        } catch (TimeoutException exception) {
            failAttempt(event, "Délai de confirmation du broker dépassé.");
            logger.warn("Outbox event {} confirmation timed out; its retry state is {}.",
                    event.getId(), event.getStatus());
        } catch (ExecutionException | AmqpException exception) {
            failAttempt(event, "Échec de livraison au broker.");
            logger.warn("Outbox event {} delivery failed; its retry state is {}.",
                    event.getId(), event.getStatus());
        }
        return true;
    }

    private void failAttempt(OutboxEvent event, String safeMessage) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(safeMessage);
        if (attempts >= maxAttempts) {
            event.setStatus(OutboxStatus.FAILED);
        } else {
            event.setStatus(OutboxStatus.PENDING);
            long delaySeconds = Math.min(3600L, 1L << Math.min(attempts - 1, 12));
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
        }
    }
}
