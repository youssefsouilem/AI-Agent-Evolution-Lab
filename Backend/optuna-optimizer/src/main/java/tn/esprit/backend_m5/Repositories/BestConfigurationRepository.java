package tn.esprit.backend_m5.Repositories;

import tn.esprit.backend_m5.Entities.BestConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BestConfigurationRepository extends JpaRepository<BestConfiguration, Long> {

    Optional<BestConfiguration> findByStudy_Id(Long studyId);

    void deleteByStudy_Id(Long studyId);
}
