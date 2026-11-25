package tn.esprit.mission.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.mission.dto.MonitoringCheckResponse;
import tn.esprit.mission.dto.MonitoringMetricsRequest;
import tn.esprit.mission.service.MonitoringService;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService service;

    public MonitoringController(MonitoringService service) {
        this.service = service;
    }

    @PostMapping("/check")
    public MonitoringCheckResponse check(@Valid @RequestBody MonitoringMetricsRequest metrics) {
        return service.check(metrics);
    }
}
