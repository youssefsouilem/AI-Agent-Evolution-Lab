package tn.esprit.mission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tn.esprit.mission.dto.MonitoringMetricsRequest;
import tn.esprit.mission.entity.MissionExecution;
import tn.esprit.mission.enums.ExecutionStatus;
import tn.esprit.mission.repository.MissionExecutionRepository;

@Service
public class PublicationService {

  private final MissionExecutionRepository executions;
  private final MonitoringService monitoring;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;
  private final boolean enabled;

  public PublicationService(
    MissionExecutionRepository executions,
    MonitoringService monitoring,
    ObjectMapper mapper,
    PlatformTransactionManager manager,
    @Value("${m6.enabled:false}") boolean enabled
  ) {
    this.executions = executions;
    this.monitoring = monitoring;
    this.mapper = mapper;
    this.transactions = new TransactionTemplate(manager);
    this.enabled = enabled;
  }

  public void autoEvaluate(Long id) {
    if (!enabled) return;
    var execution = executions.findById(id).orElseThrow();
    if (
      execution.getStatus() == ExecutionStatus.COMPLETED &&
      "MEASURED".equals(execution.getMetricsSource())
    ) evaluate(id);
  }

  public MissionExecution evaluate(Long id) {
    var execution = executions.findById(id).orElseThrow();
    if (
      execution.getStatus() != ExecutionStatus.COMPLETED ||
      execution.getCandidateResult() == null ||
      !"MEASURED".equals(execution.getMetricsSource()) ||
      execution.getTotalTokensUsed() == null ||
      execution.getStartedAt() == null ||
      execution.getFinishedAt() == null
    ) {
      throw new IllegalArgumentException(
        "Un résultat réel terminé et des métriques mesurées sont requis pour la publication."
      );
    }
    var metrics = new MonitoringMetricsRequest(
      id,
      Math.max(
        0,
        Duration.between(
          execution.getStartedAt(),
          execution.getFinishedAt()
        ).toMillis()
      ),
      execution.getTotalTokensUsed(),
      execution.getAttemptsUsed(),
      "COMPLETED"
    );
    try {
      var diagnostic = monitoring.check(metrics);
      if (
        diagnostic == null ||
        !id.equals(diagnostic.executionId()) ||
        diagnostic.blocking() == null ||
        diagnostic.anomalyDetected() == null ||
        diagnostic.severity() == null ||
        (!diagnostic.simulated() && !"M6".equals(diagnostic.source()))
      ) {
        throw new IllegalArgumentException(
          "Diagnostic M6 incomplet ou associé à une autre exécution."
        );
      }
      String json = mapper.writeValueAsString(diagnostic);
      return transactions.execute(transaction -> {
        var locked = executions.findByIdForUpdate(id).orElseThrow();
        locked.setMonitoringDiagnostic(json);
        locked.setMonitoringError(null);
        if (diagnostic.simulated()) {
          locked.setPublicationStatus("PENDING_MONITORING");
          locked.setFinalResult(null);
        } else if (Boolean.TRUE.equals(diagnostic.blocking())) {
          locked.setPublicationStatus("BLOCKED");
          locked.setFinalResult(null);
        } else {
          locked.setPublicationStatus("APPROVED");
          locked.setFinalResult(locked.getCandidateResult());
        }
        return locked;
      });
    } catch (Exception failure) {
      return transactions.execute(transaction -> {
        var locked = executions.findByIdForUpdate(id).orElseThrow();
        locked.setMonitoringError(
          "Contrôle M6 indisponible ou incompatible. Nouvelle vérification requise."
        );
        locked.setPublicationStatus("PENDING_MONITORING");
        locked.setFinalResult(null);
        return locked;
      });
    }
  }
}
