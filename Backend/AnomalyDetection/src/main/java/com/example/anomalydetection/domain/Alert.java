package com.example.anomalydetection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    @Column(nullable = false, length = 1000)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anomaly_check_id", nullable = false, unique = true)
    private AnomalyCheck anomalyCheck;

    protected Alert() {
    }

    public Alert(Severity severity, String message, AnomalyCheck anomalyCheck) {
        this.severity = severity;
        this.message = message;
        this.status = AlertStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        this.anomalyCheck = anomalyCheck;
    }

    public Long getId() { return id; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public AlertStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public AnomalyCheck getAnomalyCheck() { return anomalyCheck; }
    public void update(Severity severity, String message) { this.severity = severity; this.message = message; }
    public void acknowledge() {
        if (status != AlertStatus.OPEN) throw new IllegalStateException("Only open alerts can be acknowledged");
        status = AlertStatus.ACKNOWLEDGED;
        acknowledgedAt = LocalDateTime.now();
    }
    public void resolve() {
        if (status == AlertStatus.RESOLVED) throw new IllegalStateException("Alert is already resolved");
        status = AlertStatus.RESOLVED;
        resolvedAt = LocalDateTime.now();
    }
}
