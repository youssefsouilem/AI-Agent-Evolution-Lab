package tn.esprit.backend_m5.Repositories;

import tn.esprit.backend_m5.Entities.OptimizationStudy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptimizationStudyRepository extends JpaRepository<OptimizationStudy, Long> {

    List<OptimizationStudy> findAllByOrderByCreatedAtDesc();
}
