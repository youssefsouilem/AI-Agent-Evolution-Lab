package tn.esprit.mission.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.WorkflowEdge;

public interface WorkflowEdgeRepository
    extends JpaRepository<WorkflowEdge, Long>
{
    List<WorkflowEdge> findByMissionId(Long missionId);
    void deleteByMissionId(Long missionId);
}
