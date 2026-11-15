package tn.esprit.agentservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.agentservice.entity.Agent;

import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT agent FROM Agent agent WHERE agent.id = :id")
    Optional<Agent> findByIdForUpdate(@Param("id") Long id);
}
