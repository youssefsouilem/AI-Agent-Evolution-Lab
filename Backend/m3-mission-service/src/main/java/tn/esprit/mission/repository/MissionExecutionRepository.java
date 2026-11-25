package tn.esprit.mission.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.MissionExecution;

public interface MissionExecutionRepository
    extends JpaRepository<MissionExecution, Long>
{
    List<MissionExecution> findByMissionIdOrderByIdDesc(Long missionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from MissionExecution e where e.id = :id")
    java.util.Optional<MissionExecution> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<MissionExecution> findByStatusIn(java.util.Collection<tn.esprit.mission.enums.ExecutionStatus> statuses);

    boolean existsByMissionIdAndStatusIn(
        Long missionId,
        java.util.Collection<tn.esprit.mission.enums.ExecutionStatus> statuses
    );
}
