package com.example.anomalydetection.api;

import com.example.anomalydetection.service.AnomalyCheckService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/anomaly-checks")
public class AnomalyCheckController {
    private final AnomalyCheckService service;
    public AnomalyCheckController(AnomalyCheckService service) { this.service = service; }
    @GetMapping public List<AnomalyCheckResponse> all() { return service.findAll().stream().map(AnomalyCheckResponse::from).toList(); }
    @GetMapping("/{id}") public AnomalyCheckResponse one(@PathVariable Long id) { return AnomalyCheckResponse.from(service.findById(id)); }
    @GetMapping("/detected") public List<AnomalyCheckResponse> detected() { return service.detected().stream().map(AnomalyCheckResponse::from).toList(); }
    @GetMapping("/agent/{agentId}") public List<AnomalyCheckResponse> byAgent(@PathVariable Long agentId) { return service.byAgent(agentId).stream().map(AnomalyCheckResponse::from).toList(); }
    @GetMapping("/execution/{executionId}") public List<AnomalyCheckResponse> byExecution(@PathVariable Long executionId) { return service.byExecution(executionId).stream().map(AnomalyCheckResponse::from).toList(); }
    @GetMapping("/range") public List<AnomalyCheckResponse> range(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) { return service.between(from, to).stream().map(AnomalyCheckResponse::from).toList(); }
    @GetMapping("/summary") public Map<String, Long> summary() { return service.summary(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public AnomalyCheckResponse create(@RequestBody AnomalyCheckRequest request) { return AnomalyCheckResponse.from(service.create(request)); }
    @PutMapping("/{id}") public AnomalyCheckResponse update(@PathVariable Long id, @RequestBody AnomalyCheckRequest request) { return AnomalyCheckResponse.from(service.update(id, request)); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
