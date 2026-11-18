package com.example.anomalydetection.api;

import com.example.anomalydetection.domain.ModelVersion;
import java.time.LocalDateTime;

public record ModelVersionResponse(Long id, String name, String algorithm, Double contamination,
                                   Double thresholdScore, String features, boolean active, LocalDateTime trainedAt) {
    public static ModelVersionResponse from(ModelVersion m) {
        return new ModelVersionResponse(m.getId(), m.getName(), m.getAlgorithm(), m.getContamination(),
                m.getThresholdScore(), m.getFeatures(), m.isActive(), m.getTrainedAt());
    }
}
