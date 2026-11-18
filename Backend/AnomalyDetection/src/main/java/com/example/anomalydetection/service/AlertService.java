package com.example.anomalydetection.service;

import com.example.anomalydetection.api.AlertRequest;
import com.example.anomalydetection.domain.Alert;
import com.example.anomalydetection.domain.AlertStatus;
import com.example.anomalydetection.domain.AnomalyCheck;
import com.example.anomalydetection.domain.Severity;
import com.example.anomalydetection.repository.AlertRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AlertService {
    private final AlertRepository repository;
    private final AnomalyCheckService checks;
    public AlertService(AlertRepository repository, AnomalyCheckService checks) { this.repository = repository; this.checks = checks; }
    public List<Alert> findAll() { return repository.findAll(); }
    public Alert findById(Long id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id)); }
    @Transactional
    public Alert create(AlertRequest r) {
        if (r == null || r.anomalyCheckId() == null || r.severity() == null || r.message() == null || r.message().isBlank()) {
            throw new IllegalArgumentException("Severity, message and anomaly check id are required");
        }
        AnomalyCheck check = checks.findById(r.anomalyCheckId());
        if (check.getAlert() != null) throw new IllegalArgumentException("Anomaly check already has an alert");
        Alert alert = new Alert(parseSeverity(r.severity()), r.message(), check);
        check.setAlert(alert);
        return repository.save(alert);
    }
    @Transactional
    public Alert update(Long id, AlertRequest r) {
        if (r == null || r.severity() == null || r.message() == null || r.message().isBlank()) {
            throw new IllegalArgumentException("Severity and message are required");
        }
        Alert alert = findById(id);
        alert.update(parseSeverity(r.severity()), r.message());
        return alert;
    }
    @Transactional
    public void delete(Long id) { repository.delete(findById(id)); }
    public List<Alert> byStatus(AlertStatus status) { return repository.findByStatusOrderByCreatedAtDesc(status); }
    @Transactional public Alert acknowledge(Long id) { Alert a = findById(id); a.acknowledge(); return a; }
    @Transactional public Alert resolve(Long id) { Alert a = findById(id); a.resolve(); return a; }
    private Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown severity: " + value);
        }
    }
}
