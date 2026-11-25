package tn.esprit.mission.controller;

import java.util.List;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.mission.entity.MissionExecution;
import tn.esprit.mission.entity.MissionStepExecution;
import tn.esprit.mission.service.MissionExecutionService;

@RestController
@RequestMapping("/api")
public class MissionExecutionController {

    private final MissionExecutionService service;
    private final tn.esprit.mission.service.PublicationService publication;

    public MissionExecutionController(MissionExecutionService service, tn.esprit.mission.service.PublicationService publication) {
        this.publication = publication;
        this.service = service;
    }

    @PostMapping("/missions/{missionId}/execute")
    public ResponseEntity<MissionExecution> create(@PathVariable Long missionId) {
        MissionExecution execution = service.create(missionId);
        return ResponseEntity.accepted()
            .location(URI.create("/api/executions/" + execution.getId()))
            .body(execution);
    }

    public record ResumeRequest(@jakarta.validation.constraints.NotNull Boolean approved) {}

    @PostMapping("/executions/{executionId}/resume")
    public ResponseEntity<MissionExecution> resume(@PathVariable Long executionId,
        @jakarta.validation.Valid @RequestBody ResumeRequest request) {
        return ResponseEntity.accepted().body(service.resume(executionId, request.approved()));
    }

    @PostMapping("/executions/{executionId}/evaluate")
    public MissionExecution evaluate(@PathVariable Long executionId) {
        service.get(executionId);
        return publication.evaluate(executionId);
    }

    @GetMapping("/executions/{executionId}/attempts")
    public List<tn.esprit.mission.entity.MissionStepAttempt> attempts(@PathVariable Long executionId) {
        return service.attempts(executionId);
    }

    @GetMapping("/executions/{executionId}/steps")
    public List<MissionStepExecution> steps(@PathVariable Long executionId) {
        return service.steps(executionId);
    }

    @GetMapping("/missions/{missionId}/executions")
    public List<MissionExecution> list(@PathVariable Long missionId) {
        return service.list(missionId);
    }

    @GetMapping("/executions/{executionId}")
    public MissionExecution get(@PathVariable Long executionId) {
        return service.get(executionId);
    }
}
