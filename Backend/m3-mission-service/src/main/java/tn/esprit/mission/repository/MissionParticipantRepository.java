package tn.esprit.mission.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.MissionParticipant;

public interface MissionParticipantRepository
    extends JpaRepository<MissionParticipant, Long>
{
    List<MissionParticipant> findByMissionId(Long missionId);
    void deleteByMissionId(Long missionId);
}
