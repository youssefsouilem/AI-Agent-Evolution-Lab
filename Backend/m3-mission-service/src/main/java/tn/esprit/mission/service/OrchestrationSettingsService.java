package tn.esprit.mission.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.mission.dto.OrchestrationSettingsDto;
import tn.esprit.mission.entity.OrchestrationSettings;
import tn.esprit.mission.repository.OrchestrationSettingsRepository;

@Service
public class OrchestrationSettingsService {

    private static final Long SETTINGS_ID = 1L;

    private final OrchestrationSettingsRepository repository;

    public OrchestrationSettingsService(
        OrchestrationSettingsRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public OrchestrationSettingsDto get() {
        return repository.findById(SETTINGS_ID)
            .map(this::toDto)
            .orElseGet(this::defaults);
    }

    @Transactional
    public OrchestrationSettingsDto save(OrchestrationSettingsDto request) {
        OrchestrationSettings settings = new OrchestrationSettings(
            request.maxRetries(),
            request.maxDuration(),
            request.maxStepDuration(),
            request.maxSteps(),
            request.maxConcurrent(),
            request.allowConditions()
        );

        return toDto(repository.save(settings));
    }

    private OrchestrationSettingsDto toDto(OrchestrationSettings settings) {
        return new OrchestrationSettingsDto(
            settings.getMaxRetries(),
            settings.getMaxDuration(),
            settings.getMaxStepDuration(),
            settings.getMaxSteps(),
            settings.getMaxConcurrent(),
            settings.getAllowConditions()
        );
    }

    private OrchestrationSettingsDto defaults() {
        return new OrchestrationSettingsDto(3, 300, 30, 30, 2, true);
    }
}
