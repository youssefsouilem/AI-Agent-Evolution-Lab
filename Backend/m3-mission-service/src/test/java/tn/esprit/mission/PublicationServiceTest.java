package tn.esprit.mission;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tn.esprit.mission.dto.*;
import tn.esprit.mission.entity.MissionExecution;
import tn.esprit.mission.enums.*;
import tn.esprit.mission.repository.MissionExecutionRepository;
import tn.esprit.mission.service.*;

class PublicationServiceTest {

  MissionExecution execution;
  MissionExecutionRepository repository;
  MonitoringService monitoring;
  PublicationService service;

  @BeforeEach
  void setup() {
    repository = mock(MissionExecutionRepository.class);
    monitoring = mock(MonitoringService.class);
    service = new PublicationService(
      repository,
      monitoring,
      new ObjectMapper(),
      mock(PlatformTransactionManager.class),
      true
    );
    execution = new MissionExecution();
    execution.setId(7L);
    execution.setMissionId(3L);
    execution.setStatus(ExecutionStatus.COMPLETED);
    execution.setMetricsSource("MEASURED");
    execution.setCandidateResult("Analyse candidate");
    execution.setTotalTokensUsed(25L);
    execution.setAttemptsUsed(2);
    execution.setStartedAt(Instant.parse("2026-11-24T10:00:00Z"));
    execution.setFinishedAt(Instant.parse("2026-11-24T10:00:03Z"));
    when(repository.findById(7L)).thenReturn(Optional.of(execution));
    when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(execution));
  }

  MonitoringCheckResponse diagnostic(boolean simulated, Boolean blocking) {
    return new MonitoringCheckResponse(
      7L,
      simulated ? "SIMULATION" : "M6",
      simulated,
      Boolean.TRUE.equals(blocking),
      Boolean.TRUE.equals(blocking)
        ? AnomalySeverity.CRITICAL
        : AnomalySeverity.NORMAL,
      "Test",
      blocking
    );
  }

  @Test
  void realApprovalUsesPersistedMetricsAndPublishesCandidate() {
    when(monitoring.check(any())).thenReturn(diagnostic(false, false));
    service.evaluate(7L);
    verify(monitoring).check(
      new MonitoringMetricsRequest(7L, 3000L, 25L, 2, "COMPLETED")
    );
    assertEquals("APPROVED", execution.getPublicationStatus());
    assertEquals("Analyse candidate", execution.getFinalResult());
  }

  @Test
  void blockingDiagnosticNeverPublishes() {
    when(monitoring.check(any())).thenReturn(diagnostic(false, true));
    service.evaluate(7L);
    assertEquals("BLOCKED", execution.getPublicationStatus());
    assertNull(execution.getFinalResult());
  }

  @Test
  void simulatedDiagnosticNeverPublishes() {
    when(monitoring.check(any())).thenReturn(diagnostic(true, false));
    service.evaluate(7L);
    assertEquals("PENDING_MONITORING", execution.getPublicationStatus());
    assertNull(execution.getFinalResult());
  }

  @Test
  void unavailableMonitoringKeepsResultPending() {
    when(monitoring.check(any())).thenThrow(
      new IllegalStateException("offline")
    );
    service.evaluate(7L);
    assertEquals("PENDING_MONITORING", execution.getPublicationStatus());
    assertNotNull(execution.getMonitoringError());
    assertNull(execution.getFinalResult());
  }

  @Test
  void incompleteDiagnosticNeverPublishes() {
    when(monitoring.check(any())).thenReturn(diagnostic(false, null));
    service.evaluate(7L);
    assertEquals("PENDING_MONITORING", execution.getPublicationStatus());
    assertNull(execution.getFinalResult());
  }

  @Test
  void simulatedExecutionCannotBeSubmittedAsMeasured() {
    execution.setMetricsSource("SIMULATED");
    assertThrows(IllegalArgumentException.class, () -> service.evaluate(7L));
    verifyNoInteractions(monitoring);
  }

  @Test
  void wrongExecutionIdCannotAuthorizePublication() {
    when(monitoring.check(any())).thenReturn(
      new MonitoringCheckResponse(
        99L,
        "M6",
        false,
        false,
        AnomalySeverity.NORMAL,
        "Test",
        false
      )
    );
    service.evaluate(7L);
    assertEquals("PENDING_MONITORING", execution.getPublicationStatus());
    assertNull(execution.getFinalResult());
  }
}
