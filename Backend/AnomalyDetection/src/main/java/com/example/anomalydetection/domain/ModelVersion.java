package com.example.anomalydetection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "model_versions")
public class ModelVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String algorithm;
    private Double contamination;
    private Double thresholdScore;
    @Column(columnDefinition = "CLOB")
    private String features;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private LocalDateTime trainedAt;

    protected ModelVersion() {
    }

    public ModelVersion(String name, String algorithm, Double contamination, Double thresholdScore,
                        String features, boolean active) {
        this.name = name;
        this.algorithm = algorithm;
        this.contamination = contamination;
        this.thresholdScore = thresholdScore;
        this.features = features;
        this.active = active;
        this.trainedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAlgorithm() { return algorithm; }
    public Double getContamination() { return contamination; }
    public Double getThresholdScore() { return thresholdScore; }
    public String getFeatures() { return features; }
    public boolean isActive() { return active; }
    public LocalDateTime getTrainedAt() { return trainedAt; }
    public void update(String name, String algorithm, Double contamination, Double thresholdScore, String features) {
        this.name = name;
        this.algorithm = algorithm;
        this.contamination = contamination;
        this.thresholdScore = thresholdScore;
        this.features = features;
    }
    public void setActive(boolean active) { this.active = active; }
}
