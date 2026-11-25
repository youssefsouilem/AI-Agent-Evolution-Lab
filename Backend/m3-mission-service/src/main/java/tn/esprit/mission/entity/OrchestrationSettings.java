package tn.esprit.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orchestration_settings")
public class OrchestrationSettings {

    @Id
    private Long id;

    @Column(nullable = false)
    private Integer maxRetries;

    @Column(nullable = false)
    private Integer maxDuration;

    @Column(nullable = false)
    private Integer maxStepDuration;

    @Column(nullable = false)
    private Integer maxSteps;

    @Column(nullable = false)
    private Integer maxConcurrent;

    @Column(nullable = false)
    private Boolean allowConditions;

    protected OrchestrationSettings() {}

    public OrchestrationSettings(
        Integer maxRetries,
        Integer maxDuration,
        Integer maxStepDuration,
        Integer maxSteps,
        Integer maxConcurrent,
        Boolean allowConditions
    ) {
        this.id = 1L;
        this.maxRetries = maxRetries;
        this.maxDuration = maxDuration;
        this.maxStepDuration = maxStepDuration;
        this.maxSteps = maxSteps;
        this.maxConcurrent = maxConcurrent;
        this.allowConditions = allowConditions;
    }

    public Long getId() {
        return id;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public Integer getMaxDuration() {
        return maxDuration;
    }

    public Integer getMaxStepDuration() {
        return maxStepDuration;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public Integer getMaxConcurrent() {
        return maxConcurrent;
    }

    public Boolean getAllowConditions() {
        return allowConditions;
    }
}
