package tn.esprit.mission.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable execution journal: one row for each actual agent call. */
@Entity
@Table(name = "mission_step_attempts", uniqueConstraints = @UniqueConstraint(
    columnNames = {"missionExecutionId", "missionStepId", "attemptNumber"}))
public class MissionStepAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public Long missionExecutionId;
    @Column(nullable = false) public Long missionStepId;
    @Column(nullable = false) public Integer attemptNumber;
    public String status;
    public String agentId;
    public String agentVersionId;
    public Instant startedAt;
    public Instant finishedAt;
    public Long tokensUsed;
    @Column(columnDefinition = "LONGTEXT") public String output;
    @Column(columnDefinition = "LONGTEXT") public String errorMessage;
}
