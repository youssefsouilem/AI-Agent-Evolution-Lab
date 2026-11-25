package tn.esprit.mission.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import tn.esprit.mission.dto.*;
import tn.esprit.mission.entity.MissionStep;
import tn.esprit.mission.service.WorkflowService;

@RestController
@RequestMapping("/api/missions/{missionId}")
public class MissionWorkflowController {

    private final WorkflowService service;

    public MissionWorkflowController(WorkflowService service) {
        this.service = service;
    }

    @GetMapping("/steps")
    public List<MissionStep> steps(@PathVariable Long missionId) {
        return service.get(missionId).steps();
    }

    @GetMapping("/workflow")
    public WorkflowResponse workflow(@PathVariable Long missionId) {
        return service.get(missionId);
    }

    @PutMapping("/workflow")
    public WorkflowResponse replace(
        @PathVariable Long missionId,
        @Valid @RequestBody WorkflowRequest request
    ) {
        return service.replace(missionId, request);
    }
}
