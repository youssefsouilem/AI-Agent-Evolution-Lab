package com.example.anomalydetection.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "anomaly_checks")
public class AnomalyCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long executionId;
    @Column(nullable = false)
    private Long agentId;
    private Long agentVersionId;
    @Column(nullable = false)
    private long durationMs;
    @Column(nullable = false)
    private int tokensUsed;
    @Column(nullable = false)
    private int retryCount;
    @Column(nullable = false)
    private String executionStatus;
    @Column(nullable = false)
    private Double anomalyScore;
    @Column(nullable = false)
    private boolean anomalyDetected;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyReason reason;
    @Column(nullable = false)
    private boolean blocking;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DetectionSource source;
    @Column(nullable = false)
    private LocalDateTime checkedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;
    @OneToOne(mappedBy = "anomalyCheck", cascade = CascadeType.ALL, orphanRemoval = true)
    private Alert alert;

    protected AnomalyCheck() {
    }

    public AnomalyCheck(Long executionId, Long agentId, Long agentVersionId, long durationMs, int tokensUsed,
                        int retryCount, String executionStatus, Double anomalyScore, boolean anomalyDetected,
                        Severity severity, AnomalyReason reason, boolean blocking, DetectionSource source,
                        ModelVersion modelVersion) {
        this.executionId = executionId;
        this.agentId = agentId;
        this.agentVersionId = agentVersionId;
        this.durationMs = durationMs;
        this.tokensUsed = tokensUsed;
        this.retryCount = retryCount;
        this.executionStatus = executionStatus;
        this.anomalyScore = anomalyScore;
        this.anomalyDetected = anomalyDetected;
        this.severity = severity;
        this.reason = reason;
        this.blocking = blocking;
        this.source = source;
        this.modelVersion = modelVersion;
        this.checkedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getExecutionId() { return executionId; }
    public Long getAgentId() { return agentId; }
    public Long getAgentVersionId() { return agentVersionId; }
    public long getDurationMs() { return durationMs; }
    public int getTokensUsed() { return tokensUsed; }
    public int getRetryCount() { return retryCount; }
    public String getExecutionStatus() { return executionStatus; }
    public Double getAnomalyScore() { return anomalyScore; }
    public boolean isAnomalyDetected() { return anomalyDetected; }
    public Severity getSeverity() { return severity; }
    public AnomalyReason getReason() { return reason; }
    public boolean isBlocking() { return blocking; }
    public DetectionSource getSource() { return source; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public ModelVersion getModelVersion() { return modelVersion; }
    public Alert getAlert() { return alert; }
    public void update(Long agentId, Long agentVersionId, long durationMs, int tokensUsed, int retryCount,
                       String executionStatus) {
        this.agentId = agentId;
        this.agentVersionId = agentVersionId;
        this.durationMs = durationMs;
        this.tokensUsed = tokensUsed;
        this.retryCount = retryCount;
        this.executionStatus = executionStatus;
    }
    public void setAlert(Alert alert) { this.alert = alert; }
}
