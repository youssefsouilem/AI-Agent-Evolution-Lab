package tn.esprit.agentservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.RejectedExecutionException;

@Component
public class AgentEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(AgentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final TaskExecutor eventTaskExecutor;
    private final String exchange;

    public AgentEventPublisher(
            RabbitTemplate rabbitTemplate,
            TaskExecutor eventTaskExecutor,
            @Value("${app.events.agent-exchange:agent.events}") String exchange
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.eventTaskExecutor = eventTaskExecutor;
        this.exchange = exchange;
    }

    public void publish(AgentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(event);
                }
            });
            return;
        }
        dispatch(event);
    }

    private void dispatch(AgentEvent event) {
        try {
            eventTaskExecutor.execute(() -> {
                try {
                    rabbitTemplate.convertAndSend(exchange, event.eventType().name(), event, message -> {
                        message.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT
                        );
                        return message;
                    });
                } catch (RuntimeException exception) {
                    logger.warn("Could not publish agent event {}. Continuing without broker delivery.",
                            event.eventId(), exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            logger.warn("Agent event queue is full; event {} was not published.", event.eventId(), exception);
        }
    }
}
