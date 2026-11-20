package tn.esprit.agentservice.event;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.esprit.agentservice.service.OutboxDispatchService;

@Component
public class OutboxDispatcher {
    private final OutboxDispatchService dispatchService;

    public OutboxDispatcher(OutboxDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${m1.outbox.poll-interval-ms:2000}")
    public void dispatchPendingEvents() {
        for (int i = 0; i < 100; i++) {
            if (!dispatchService.dispatchOne()) return;
        }
    }
}
