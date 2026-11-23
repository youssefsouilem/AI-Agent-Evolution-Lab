package tn.esprit.agentservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.agentservice.entity.UserQuota;

import java.util.Optional;

public interface UserQuotaRepository extends JpaRepository<UserQuota, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quota from UserQuota quota where quota.ownerId = :ownerId")
    Optional<UserQuota> findByOwnerIdForUpdate(@Param("ownerId") String ownerId);

    @Modifying
    @Query(value = "MERGE INTO user_quota (owner_id) KEY (owner_id) VALUES (:ownerId)", nativeQuery = true)
    void ensureRow(@Param("ownerId") String ownerId);
}
