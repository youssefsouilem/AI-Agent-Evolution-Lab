package tn.esprit.agentservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import tn.esprit.agentservice.entity.OutboxEvent;
import tn.esprit.agentservice.entity.OutboxStatus;
import tn.esprit.agentservice.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxDispatchServiceTest {
    @Test
    void positiveConfirmMarksEventSent() {
        OutboxEvent event = pending();
        OutboxEventRepository repository = readyRepository(event);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq("events"), eq("agent.created"), eq("{}"), any(CorrelationData.class));

        assertTrue(new OutboxDispatchService(repository, rabbit, "events", 10, 100)
                .dispatchOne());
        assertEquals(OutboxStatus.SENT, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertTrue(event.getSentAt() != null);
    }

    @Test
    void negativeConfirmRetriesThenExhaustsAttempts() {
        OutboxEvent event = pending();
        event.setAttempts(1);
        OutboxEventRepository repository = readyRepository(event);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "not exposed"));
            return null;
        }).when(rabbit).convertAndSend(eq("events"), eq("agent.created"), eq("{}"), any(CorrelationData.class));

        new OutboxDispatchService(repository, rabbit, "events", 2, 100).dispatchOne();
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(2, event.getAttempts());
        assertEquals("Le broker a refusé la confirmation.", event.getLastError());
    }

    @Test
    void confirmationTimeoutLeavesEventPendingAndSchedulesRetry() {
        OutboxEvent event = pending();
        OutboxEventRepository repository = readyRepository(event);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);

        new OutboxDispatchService(repository, rabbit, "events", 10, 1).dispatchOne();
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertTrue(event.getNextAttemptAt().isAfter(LocalDateTime.now()));
    }

    private OutboxEventRepository readyRepository(OutboxEvent event) {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.lockReadyEvents(eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(List.of(event));
        return repository;
    }

    private OutboxEvent pending() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType("AGENT_CREATED");
        event.setRoutingKey("agent.created");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PENDING);
        event.setCreatedAt(LocalDateTime.now());
        event.setNextAttemptAt(LocalDateTime.now());
        return event;
    }
}
