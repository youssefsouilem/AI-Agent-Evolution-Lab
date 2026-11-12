package tn.esprit.backend_m5.Repositories;

import tn.esprit.backend_m5.Entities.Trial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrialRepository extends JpaRepository<Trial, Long> {

    List<Trial> findByStudy_IdOrderByTrialNumberAsc(Long studyId);

    long countByStudy_Id(Long studyId);
}
