package com.example.anomalydetection.api;

import com.example.anomalydetection.domain.AlertStatus;
import com.example.anomalydetection.service.AlertService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService service;
    public AlertController(AlertService service) { this.service = service; }
    @GetMapping public List<AlertResponse> all() { return service.findAll().stream().map(AlertResponse::from).toList(); }
    @GetMapping("/{id}") public AlertResponse one(@PathVariable Long id) { return AlertResponse.from(service.findById(id)); }
    @GetMapping("/status") public List<AlertResponse> byStatus(@RequestParam AlertStatus status) { return service.byStatus(status).stream().map(AlertResponse::from).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public AlertResponse create(@RequestBody AlertRequest request) { return AlertResponse.from(service.create(request)); }
    @PutMapping("/{id}") public AlertResponse update(@PathVariable Long id, @RequestBody AlertRequest request) { return AlertResponse.from(service.update(id, request)); }
    @PostMapping("/{id}/acknowledge") public AlertResponse acknowledge(@PathVariable Long id) { return AlertResponse.from(service.acknowledge(id)); }
    @PostMapping("/{id}/resolve") public AlertResponse resolve(@PathVariable Long id) { return AlertResponse.from(service.resolve(id)); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
