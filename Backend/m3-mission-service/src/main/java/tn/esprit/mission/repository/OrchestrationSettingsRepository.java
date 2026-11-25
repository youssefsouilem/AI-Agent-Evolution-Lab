package tn.esprit.mission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.OrchestrationSettings;

public interface OrchestrationSettingsRepository
    extends JpaRepository<OrchestrationSettings, Long> {}
