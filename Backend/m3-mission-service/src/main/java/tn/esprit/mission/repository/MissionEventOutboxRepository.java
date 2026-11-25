package tn.esprit.mission.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.mission.entity.MissionEventOutbox;

public interface MissionEventOutboxRepository extends JpaRepository<MissionEventOutbox, String> {
    boolean existsByExecutionIdAndEventType(Long executionId, String eventType);
    List<MissionEventOutbox> findTop20ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant now);
}
