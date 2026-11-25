package tn.esprit.mission.service;

import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.esprit.mission.enums.ExecutionStatus;
import tn.esprit.mission.repository.MissionExecutionRepository;

@Service
public class OrchestrationBridge {
    private final OrchestratorClient client;
    private final PublicationService publication;
    private final ExecutionStateStore store;
    private final MissionExecutionRepository executions;
    public OrchestrationBridge(OrchestratorClient client, ExecutionStateStore store, MissionExecutionRepository executions, PublicationService publication) {
        this.publication = publication;
        this.client = client; this.store = store; this.executions = executions;
    }
    public boolean enabled() { return client.enabled(); }
    @Async("orchestrationExecutor")
    public void launch(Long executionId) { dispatch(executionId, null); }
    @Async("orchestrationExecutor")
    public void resume(Long executionId, boolean approved) { dispatch(executionId, approved); }
    private void dispatch(Long id, Boolean approved) {
        try {
            var request = store.request(id);
            store.begin(id);
            var snapshot = approved == null ? client.execute(request) : client.resume(id, approved, request.maxDuration());
            store.apply(id, snapshot);
            publication.autoEvaluate(id);
        } catch (Exception error) {
            store.uncertain(id, "Communication moteur incertaine : vérification de l'état nécessaire. Aucun renvoi automatique.");
            refresh(id);
        }
    }
    public void refresh(Long id) {
        if (!enabled()) return;
        var current = executions.findById(id).orElse(null);
        if (current == null || current.getOrchestratorRequest() == null ||
            !List.of(ExecutionStatus.RUNNING, ExecutionStatus.RECOVERY_REQUIRED, ExecutionStatus.PAUSED).contains(current.getStatus())) return;
        try { store.apply(id, client.get(id)); publication.autoEvaluate(id); }
        catch (Exception error) {
            // Never resend an execution merely because its state could not be read.
            if (current.getStatus() == ExecutionStatus.RECOVERY_REQUIRED) {
                store.uncertain(id, "État du moteur indisponible. Conserver cette exécution pour réconciliation.");
            }
        }
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        if (!enabled()) return;
        for (var execution : executions.findByStatusIn(List.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING, ExecutionStatus.RECOVERY_REQUIRED))) {
            if (execution.getOrchestratorRequest() == null) continue;
            store.uncertain(execution.getId(), "Redémarrage du backend : état à réconcilier sans relancer les agents.");
            refresh(execution.getId());
        }
    }
}
