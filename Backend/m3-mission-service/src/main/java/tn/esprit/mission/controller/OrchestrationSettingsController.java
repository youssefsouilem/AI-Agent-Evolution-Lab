package tn.esprit.mission.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.mission.dto.OrchestrationSettingsDto;
import tn.esprit.mission.service.OrchestrationSettingsService;

@RestController
@RequestMapping("/api/orchestration-settings")
public class OrchestrationSettingsController {

    private final OrchestrationSettingsService service;

    public OrchestrationSettingsController(OrchestrationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public OrchestrationSettingsDto get() {
        return service.get();
    }

    @PutMapping
    public OrchestrationSettingsDto save(
        @Valid @RequestBody OrchestrationSettingsDto request
    ) {
        return service.save(request);
    }
}
