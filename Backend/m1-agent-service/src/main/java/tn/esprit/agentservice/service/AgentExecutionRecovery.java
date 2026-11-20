package tn.esprit.agentservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AgentExecutionRecovery {
    private final AgentExecutionStore store;
    private final long staleAfterMinutes;

    public AgentExecutionRecovery(
            AgentExecutionStore store,
            @Value("${m1.executions.stale-after-minutes:5}") long staleAfterMinutes
    ) {
        this.store = store;
        this.staleAfterMinutes = staleAfterMinutes;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markStaleExecutions() {
        store.failStale(LocalDateTime.now().minusMinutes(staleAfterMinutes));
    }
}
