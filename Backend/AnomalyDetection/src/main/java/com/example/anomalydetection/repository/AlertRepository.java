package com.example.anomalydetection.repository;

import com.example.anomalydetection.domain.Alert;
import com.example.anomalydetection.domain.AlertStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);
    List<Alert> findBySeverityOrderByCreatedAtDesc(com.example.anomalydetection.domain.Severity severity);
}
