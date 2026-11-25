package tn.esprit.mission.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.MissionStepAttempt;

public interface MissionStepAttemptRepository extends JpaRepository<MissionStepAttempt, Long> {
    boolean existsByMissionExecutionIdAndMissionStepIdAndAttemptNumber(Long executionId, Long stepId, Integer attemptNumber);
    List<MissionStepAttempt> findByMissionExecutionIdOrderByIdAsc(Long executionId);
}
