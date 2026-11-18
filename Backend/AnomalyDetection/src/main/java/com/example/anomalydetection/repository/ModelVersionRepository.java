package com.example.anomalydetection.repository;

import com.example.anomalydetection.domain.ModelVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Long> {
    List<ModelVersion> findByActiveTrueOrderByTrainedAtDesc();
    boolean existsByNameIgnoreCase(String name);
}
