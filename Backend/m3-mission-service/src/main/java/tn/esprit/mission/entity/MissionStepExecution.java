package tn.esprit.mission.entity;

import jakarta.persistence.*;
import java.time.Instant;
import tn.esprit.mission.enums.ExecutionStatus;

@Entity
@Table(name = "mission_step_executions")
public class MissionStepExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long missionExecutionId;

    @Column(nullable = false)
    private Long missionStepId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExecutionStatus status;

    private Instant startedAt;

    private Instant finishedAt;

    @Column(columnDefinition = "LONGTEXT")
    private String output;

    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    private Long tokensUsed;

    private Integer attemptNumber;

    public MissionStepExecution() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMissionExecutionId() {
        return missionExecutionId;
    }

    public void setMissionExecutionId(Long missionExecutionId) {
        this.missionExecutionId = missionExecutionId;
    }

    public Long getMissionStepId() {
        return missionStepId;
    }

    public void setMissionStepId(Long missionStepId) {
        this.missionStepId = missionStepId;
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

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }
}
