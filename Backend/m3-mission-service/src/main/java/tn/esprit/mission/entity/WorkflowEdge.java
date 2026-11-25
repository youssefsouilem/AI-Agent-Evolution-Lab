package tn.esprit.mission.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflow_edges")
public class WorkflowEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long missionId;

    @Column(nullable = false)
    private Long sourceStepId;

    @Column(nullable = false)
    private Long targetStepId;

    @Column(length = 200)
    private String conditionLabel;

    private Boolean correction = false;

    public boolean isCorrection() { return Boolean.TRUE.equals(correction); }
    public void setCorrection(Boolean correction) { this.correction = Boolean.TRUE.equals(correction); }

    public WorkflowEdge() {}

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

    public Long getSourceStepId() {
        return sourceStepId;
    }

    public void setSourceStepId(Long sourceStepId) {
        this.sourceStepId = sourceStepId;
    }

    public Long getTargetStepId() {
        return targetStepId;
    }

    public void setTargetStepId(Long targetStepId) {
        this.targetStepId = targetStepId;
    }

    public String getConditionLabel() {
        return conditionLabel;
    }

    public void setConditionLabel(String conditionLabel) {
        this.conditionLabel = conditionLabel;
    }
}
