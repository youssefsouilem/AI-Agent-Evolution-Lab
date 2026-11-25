package tn.esprit.mission.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.MissionStepExecution;

public interface MissionStepExecutionRepository
    extends JpaRepository<MissionStepExecution, Long>
{
    List<MissionStepExecution> findByMissionExecutionId(
        Long missionExecutionId
    );
}
