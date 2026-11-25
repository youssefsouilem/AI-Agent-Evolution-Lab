package tn.esprit.mission.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.MissionStep;

public interface MissionStepRepository
    extends JpaRepository<MissionStep, Long>
{
    List<MissionStep> findByMissionIdOrderByStepOrderAsc(Long missionId);
    void deleteByMissionId(Long missionId);
}
