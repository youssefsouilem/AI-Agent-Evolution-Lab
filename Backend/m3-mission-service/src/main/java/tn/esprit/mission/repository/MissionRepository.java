package tn.esprit.mission.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import tn.esprit.mission.entity.Mission;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mission m where m.id = :id")
    Optional<Mission> findByIdForUpdate(@Param("id") Long id);

    Page<Mission> findByOwnerIdAndNameContainingIgnoreCase(String ownerId, String search, Pageable pageable);

    Page<Mission> findByNameContainingIgnoreCase(
        String search,
        Pageable pageable
    );
}
