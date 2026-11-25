package tn.esprit.mission.entity;

import jakarta.persistence.*;
import java.time.Instant;
import tn.esprit.mission.enums.ExecutionStatus;

@Entity
@Table(name = "mission_executions")
public class MissionExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long missionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExecutionStatus status;

    private Instant startedAt;

    private Instant requestedAt;

    private Instant finishedAt;

    @Column(columnDefinition = "LONGTEXT")
    private String finalResult;

    private Long totalTokensUsed;

    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    private Integer attemptsUsed;

    @Column(columnDefinition = "LONGTEXT")
    private String orchestratorRequest;
    @Column(columnDefinition = "LONGTEXT")
    private String orchestratorState;
    @Column(columnDefinition = "LONGTEXT")
    private String candidateResult;
    private String metricsSource;
    private String publicationStatus;
    @Column(columnDefinition = "LONGTEXT") private String monitoringDiagnostic;
    @Column(columnDefinition = "LONGTEXT") private String monitoringError;
    public String getMonitoringDiagnostic() { return monitoringDiagnostic; }
    public void setMonitoringDiagnostic(String value) { monitoringDiagnostic = value; }
    public String getMonitoringError() { return monitoringError; }
    public void setMonitoringError(String value) { monitoringError = value; }

    public String getOrchestratorRequest() { return orchestratorRequest; }
    public void setOrchestratorRequest(String value) { orchestratorRequest = value; }
    public String getOrchestratorState() { return orchestratorState; }
    public void setOrchestratorState(String value) { orchestratorState = value; }
    public String getCandidateResult() { return candidateResult; }
    public void setCandidateResult(String value) { candidateResult = value; }
    public String getMetricsSource() { return metricsSource; }
    public void setMetricsSource(String value) { metricsSource = value; }
    public String getPublicationStatus() { return publicationStatus; }
    public void setPublicationStatus(String value) { publicationStatus = value; }
    public MissionExecution() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public void setMissionId(Long missionId) {
        this.missionId = missionId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(String finalResult) {
        this.finalResult = finalResult;
    }

    public Long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    public void setTotalTokensUsed(Long totalTokensUsed) {
        this.totalTokensUsed = totalTokensUsed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getAttemptsUsed() {
        return attemptsUsed;
    }

    public void setAttemptsUsed(Integer attemptsUsed) {
        this.attemptsUsed = attemptsUsed;
    }
}
