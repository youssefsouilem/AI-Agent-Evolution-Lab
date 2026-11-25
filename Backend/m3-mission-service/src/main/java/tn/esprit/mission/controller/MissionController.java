package tn.esprit.mission.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tn.esprit.mission.dto.MissionRequest;
import tn.esprit.mission.entity.Mission;
import tn.esprit.mission.service.MissionService;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionService service;
    private final tn.esprit.mission.service.WorkflowGenerationService generation;

    public MissionController(MissionService service, tn.esprit.mission.service.WorkflowGenerationService generation) {
        this.generation = generation;
        this.service = service;
    }

    public record GenerateRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 4000) String instruction) {}

    @PostMapping("/{id}/generate")
    public tn.esprit.mission.dto.WorkflowRequest generate(@PathVariable Long id, @Valid @RequestBody GenerateRequest request) {
        return generation.generate(id, request.instruction());
    }

    @PostMapping
    public ResponseEntity<Mission> create(
        @Valid @RequestBody MissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.create(request)
        );
    }

    @GetMapping
    public Page<Mission> list(
        @RequestParam(defaultValue = "") String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        if (
            page < 0 || size < 1 || size > 100
        ) throw new IllegalArgumentException("Pagination invalide");
        return service.list(
            search,
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }

    @GetMapping("/{id}")
    public Mission get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public Mission update(
        @PathVariable Long id,
        @Valid @RequestBody MissionRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<Mission> duplicate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.duplicate(id)
        );
    }
}
